package com.dailygallery.app.data

import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.Day
import com.dailygallery.app.domain.Pool
import com.dailygallery.app.domain.PoolEntry
import com.dailygallery.app.domain.Source
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Puts each day's artwork together: the museum's record (required) and the Wikipedia articles on
 * the work and its artist (optional), read side by side. A finished artwork is cached; one missing
 * Wikipedia because it couldn't be reached is shown, cached, and read again on the next visit.
 */
class ArtworkRepository(
    private val pool: Pool,
    private val aic: AicSource,
    private val cma: ClevelandSource,
    private val wikipedia: WikipediaSource,
    private val cache: ArtworkCache,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    fun today(): Day = clock().let { day -> Day(day, pool.entryFor(day)) }

    /** Today and the days before it, newest first. */
    fun recentDays(count: Int): List<Day> = pool.daysUpTo(clock(), count)

    /**
     * The artwork for [entry]: the cached copy when it is complete, otherwise read from the sources.
     * When they can't be reached, an incomplete cached copy is better than nothing.
     * [refresh] reads the sources even when the copy is complete.
     */
    suspend fun artwork(entry: PoolEntry, refresh: Boolean = false): Artwork = withContext(io) {
        val cached = cache.read(entry.key)
        if (cached != null && !cached.incomplete && !refresh) return@withContext cached
        try {
            fetch(entry).also { cache.write(entry.key, it) }
        } catch (failure: FetchException) {
            cached ?: throw failure
        }
    }

    private suspend fun fetch(entry: PoolEntry): Artwork = coroutineScope {
        val work = entry.wikipedia?.let { title -> async { attempt { wikipedia.article(title) } } }
        val artist = entry.artistWikipedia?.let { title -> async { attempt { wikipedia.article(title) } } }
        val museum = when (entry.source) {
            Source.AIC -> aic.artwork(entry.id)
            Source.CMA -> cma.artwork(entry.id)
        }
        val workArticle = work?.await()
        val artistArticle = artist?.await()
        museum.withWikipedia(workArticle?.getOrNull(), artistArticle?.getOrNull())
            .copy(incomplete = workArticle?.isFailure == true || artistArticle?.isFailure == true)
    }
}
