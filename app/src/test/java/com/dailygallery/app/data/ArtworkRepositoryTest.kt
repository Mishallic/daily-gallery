package com.dailygallery.app.data

import com.dailygallery.app.domain.Pool
import com.dailygallery.app.domain.PoolEntry
import com.dailygallery.app.domain.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

/** Two days: La Grande Jatte (Art Institute) and The Red Kerchief (Cleveland), each with Wikipedia. */
class ArtworkRepositoryTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val jatte = PoolEntry(
        Source.AIC, "27992", "A Sunday on La Grande Jatte — 1884", "Georges Seurat", "1884–86", "https://example.org/t.jpg",
        wikipedia = "A Sunday Afternoon on the Island of La Grande Jatte", artistWikipedia = "Georges Seurat",
    )
    private val kerchief = PoolEntry(
        Source.CMA, "135382", "The Red Kerchief", "Claude Monet", "c. 1868–73", "https://example.org/k.jpg",
        artistWikipedia = "Claude Monet",
    )
    private val start = LocalDate.of(2026, 10, 16)
    private val pool = Pool(start, listOf(jatte, kerchief))

    private var wikipediaUp = true
    private var museumsUp = true
    private val http = FakeHttp(
        "https://api.artic.edu/api/v1/artworks/27992" to { museum("aic-artwork-27992.json") },
        "https://api.artic.edu/api/v1/agents/40810" to { museum("aic-agent-40810.json") },
        "https://openaccess-api.clevelandart.org/api/artworks/135382" to { museum("cma-artwork-135382.json") },
        "https://en.wikipedia.org/w/api.php" to { url -> wikipedia(url) },
    )

    private fun museum(name: String): String = if (museumsUp) fixture(name) else throw FetchException.Offline()

    private fun wikipedia(url: String): String = when {
        !wikipediaUp -> throw FetchException.Offline()
        "titles=A+Sunday" in url -> fixture("wikipedia-grande-jatte.json")
        "titles=Georges+Seurat" in url -> fixture("wikipedia-seurat.json")
        "titles=Claude+Monet" in url -> fixture("wikipedia-monet.json")
        else -> throw FetchException.NotFound(url)
    }

    private var day = start
    private val repository by lazy { // the temporary folder exists only once the test starts
        ArtworkRepository(pool, AicSource(http), ClevelandSource(http), WikipediaSource(http), ArtworkCache(folder.root), clock = { day })
    }

    @Test
    fun eachDayShowsItsEntry() {
        assertEquals(jatte, repository.today().entry)
        day = start.plusDays(1)
        assertEquals(kerchief, repository.today().entry)
        assertEquals(listOf(kerchief, jatte), repository.recentDays(5).map { it.entry })
    }

    @Test
    fun anArtworkCombinesTheMuseumAndWikipediaAndIsReadOnce() = runBlocking<Unit> {
        val artwork = repository.artwork(jatte)
        assertEquals("A Sunday on La Grande Jatte — 1884", artwork.title)
        assertEquals(listOf("Interpretation"), artwork.critics.map { it.title })
        assertEquals(listOf("Influence"), artwork.artistCritics.map { it.title })
        assertFalse(artwork.incomplete)

        val requests = http.requested.size
        assertEquals(artwork, repository.artwork(jatte))
        assertEquals(requests, http.requested.size) // from the cache
    }

    @Test
    fun clevelandWorksTakeTheArtistsCriticsFromWikipedia() = runBlocking<Unit> {
        val artwork = repository.artwork(kerchief)
        assertEquals("Claude Monet", artwork.artist)
        assertTrue(artwork.artistCritics.isNotEmpty())
        assertEquals("Cleveland Museum of Art", artwork.artistBio!!.source) // the museum's own biography first
    }

    @Test
    fun withoutWikipediaTheArtworkStillShowsAndIsCompletedNextTime() = runBlocking<Unit> {
        wikipediaUp = false
        val partial = repository.artwork(jatte)
        assertTrue(partial.incomplete)
        assertTrue(partial.critics.isEmpty())
        assertEquals("Georges Seurat", partial.artist)

        wikipediaUp = true
        val complete = repository.artwork(jatte)
        assertFalse(complete.incomplete)
        assertEquals(listOf("Interpretation"), complete.critics.map { it.title })
    }

    @Test
    fun offlineTheCachedCopyIsUsedAndWithoutOneTheErrorIsReported() = runBlocking<Unit> {
        wikipediaUp = false
        repository.artwork(jatte) // cached, incomplete
        museumsUp = false
        assertEquals("Georges Seurat", repository.artwork(jatte).artist)
        assertThrows(FetchException.Offline::class.java) { runBlocking { repository.artwork(kerchief) } }
    }
}
