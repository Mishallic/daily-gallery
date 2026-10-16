package com.dailygallery.app.ui

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.test.FakeImage
import coil3.test.FakeImageLoaderEngine
import com.dailygallery.app.AppGraph
import com.dailygallery.app.data.FakeHttp
import com.dailygallery.app.data.FetchException
import com.dailygallery.app.data.fixture
import com.dailygallery.app.domain.Pool
import com.dailygallery.app.domain.PoolEntry
import com.dailygallery.app.domain.Source
import java.io.File
import java.time.LocalDate

/**
 * The app on saved responses: a two-day schedule (La Grande Jatte, then The Red Kerchief), the
 * museums' and Wikipedia's saved answers, and the real images, so tests and screenshots send no
 * requests. Switch [museumsUp] or [wikipediaUp] off to stand in for a failing service.
 */
class TestServices {
    var museumsUp = true
    var wikipediaUp = true

    val start: LocalDate = LocalDate.of(2026, 10, 16)
    val jatte = PoolEntry(
        Source.AIC, "27992", "A Sunday on La Grande Jatte — 1884", "Georges Seurat", "1884–86, border added 1888–89",
        "https://www.artic.edu/iiif/2/2d484387-2509-5e8e-2c43-22f9981972eb/full/400,/0/default.jpg",
        wikipedia = "A Sunday Afternoon on the Island of La Grande Jatte", artistWikipedia = "Georges Seurat",
    )
    val kerchief = PoolEntry(
        Source.CMA, "135382", "The Red Kerchief", "Claude Monet", "c. 1868–73",
        "https://openaccess-cdn.clevelandart.org/1958.39/1958.39_web.jpg", artistWikipedia = "Claude Monet",
    )
    val pool = Pool(start, listOf(jatte, kerchief))

    val http = FakeHttp(
        "https://api.artic.edu/api/v1/artworks/27992" to { museum("aic-artwork-27992.json") },
        "https://api.artic.edu/api/v1/agents/40810" to { museum("aic-agent-40810.json") },
        "https://openaccess-api.clevelandart.org/api/artworks/135382" to { museum("cma-artwork-135382.json") },
        "https://en.wikipedia.org/w/api.php" to { url -> wikipedia(url) },
    )

    private fun museum(name: String) = if (museumsUp) fixture(name) else throw FetchException.Offline()

    private fun wikipedia(url: String): String = when {
        !wikipediaUp -> throw FetchException.Offline()
        "titles=A+Sunday" in url -> fixture("wikipedia-grande-jatte.json")
        "titles=Georges+Seurat" in url -> fixture("wikipedia-seurat.json")
        "titles=Claude+Monet" in url -> fixture("wikipedia-monet.json")
        else -> throw FetchException.NotFound(url)
    }

    fun graph(context: Context, today: LocalDate = start) = AppGraph(context, http, clock = { today }, pool = pool)

    /** Coil answers with the saved images (any size of the same picture); anything else is grey. */
    @OptIn(ExperimentalCoilApi::class)
    fun useSavedImages(context: Context) {
        val engine = FakeImageLoaderEngine.Builder()
            .intercept({ "2d484387" in it.toString() }, image("aic-27992.jpg"))
            .intercept({ "1958.39" in it.toString() }, image("cma-135382.jpg"))
            .default(FakeImage(color = 0xFF8A8172.toInt()))
            .build()
        SingletonImageLoader.setUnsafe(ImageLoader.Builder(context).components { add(engine) }.build())
    }

    private fun image(name: String) =
        TestServices::class.java.getResourceAsStream("/images/$name")!!.use { BitmapFactory.decodeStream(it) }.asImage()
}

/**
 * Saves the activity's window to build/screenshots/[name].png. Compose's captureToImage() waits for
 * a frame that never comes under Robolectric, so this draws the window into a bitmap directly.
 */
fun Activity.screenshot(name: String) {
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    val dir = File("build/screenshots").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
}
