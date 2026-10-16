package com.dailygallery.app

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.dailygallery.app.data.AicSource
import com.dailygallery.app.data.ArtworkCache
import com.dailygallery.app.data.ArtworkRepository
import com.dailygallery.app.data.ClevelandSource
import com.dailygallery.app.data.HttpGet
import com.dailygallery.app.data.OkHttpGet
import com.dailygallery.app.data.SavedStore
import com.dailygallery.app.data.WikipediaSource
import com.dailygallery.app.domain.Pool
import com.dailygallery.app.domain.PoolEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toOkioPath
import java.io.File
import java.time.LocalDate

class DailyGalleryApp : Application(), SingletonImageLoader.Factory {
    val graph: AppGraph by lazy { graphFactory(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpGet.defaultClient() })) }
        .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("images").toOkioPath()).maxSizeBytes(100L shl 20).build() }
        .crossfade(true)
        .build()

    companion object {
        /** Tests swap in saved responses and a fixed date before the app starts. */
        var graphFactory: (Context) -> AppGraph = { AppGraph(it) }
    }
}

/** The app's objects, made once: the schedule, where artworks come from, and what the user saved. */
class AppGraph(
    context: Context,
    http: HttpGet = OkHttpGet(),
    clock: () -> LocalDate = { LocalDate.now() },
    val pool: Pool = context.assets.open("pool.json").bufferedReader().use { Pool.parse(it.readText()) },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val saved = SavedStore(File(context.filesDir, "saved.json"), scope)

    val repository = ArtworkRepository(
        pool = pool,
        aic = AicSource(http),
        cma = ClevelandSource(http),
        wikipedia = WikipediaSource(http),
        cache = ArtworkCache(File(context.filesDir, "artworks"), pinned = saved::keys), // saved ones stay offline
        clock = clock,
    )

    /** An artwork by its key: from the schedule, or from the saved list. */
    fun entry(key: String): PoolEntry? =
        pool.entries.firstOrNull { it.key == key } ?: saved.entries.value.firstOrNull { it.key == key }
}
