package com.dailygallery.app.data

import com.dailygallery.app.domain.Pool
import com.dailygallery.app.domain.Source
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The first [DAYS] days of the shipped schedule, read from the real museums and Wikipedia. Off by
 * default (the services are outside our control); run it with the environment variable
 * DAILY_GALLERY_LIVE=1, e.g. after rebuilding the pool or when a source may have changed.
 */
class LiveSourcesTest {
    private val pool = Pool.parse(File("src/main/assets/pool.json").readText())
    private val client = OkHttpGet.defaultClient()
    private val http = OkHttpGet(client)

    @Test
    fun theComingDaysReadFromTheRealSources() = runBlocking {
        assumeTrue("set DAILY_GALLERY_LIVE=1 to run", System.getenv("DAILY_GALLERY_LIVE") == "1")
        val aic = AicSource(http)
        val cma = ClevelandSource(http)
        val wikipedia = WikipediaSource(http)
        val problems = mutableListOf<String>()

        for (entry in pool.entries.take(DAYS)) {
            val museum = when (entry.source) {
                Source.AIC -> aic.artwork(entry.id)
                Source.CMA -> cma.artwork(entry.id)
            }
            val work = entry.wikipedia?.let { wikipedia.article(it) }
            val artist = entry.artistWikipedia?.let { wikipedia.article(it) }
            val artwork = museum.withWikipedia(work, artist)

            val image = artwork.image
            val imageStatus = image?.let { client.newCall(Request.Builder().url(it.url).build()).execute().use { r -> r.code } }
            println(
                "%-4s %-8s %-48.48s story %d · facts %d · owners %d · shows %d · pubs %d · critics %d/%d · bio %s · image %s".format(
                    entry.source.name, entry.id, artwork.title, artwork.story.size, artwork.facts.size, artwork.owners.size,
                    artwork.exhibitions.size, artwork.publications.size, artwork.critics.size, artwork.artistCritics.size,
                    if (artwork.artistBio != null) "yes" else "no", imageStatus,
                ),
            )
            if (artwork.title.isBlank()) problems += "${entry.key}: no title"
            if (imageStatus != 200) problems += "${entry.key}: image answered $imageStatus"
            if (artwork.story.isEmpty() && artwork.facts.isEmpty()) problems += "${entry.key}: nothing to read"
            if (entry.wikipedia != null && work == null) problems += "${entry.key}: no article \"${entry.wikipedia}\""
            if (entry.artistWikipedia != null && artist == null) problems += "${entry.key}: no article \"${entry.artistWikipedia}\""
            Thread.sleep(500) // the Art Institute allows 60 requests a minute
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        const val DAYS = 30
    }
}
