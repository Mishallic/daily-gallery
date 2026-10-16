package com.dailygallery.app.data

import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtworkCacheTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun artwork(id: String) = Artwork(Source.AIC, id, "Title $id", "Artist", date = "1900", pageUrl = "https://example.org/$id")

    @Test
    fun anArtworkReadsBackAsItWasWritten() {
        val cache = ArtworkCache(folder.root)
        cache.write("aic-1", artwork("1"))
        assertEquals(artwork("1"), cache.read("aic-1"))
        assertNull(cache.read("aic-2"))
    }

    @Test
    fun anUnreadableFileIsDroppedSoTheArtworkIsReadAgain() {
        val cache = ArtworkCache(folder.root)
        File(folder.root, "aic-1.json").writeText("{not json")
        assertNull(cache.read("aic-1"))
        assertFalse(File(folder.root, "aic-1.json").exists())
    }

    @Test
    fun onlyTheMostRecentlyOpenedAreKeptAndPinnedOnesAlways() {
        val cache = ArtworkCache(folder.root, maxFiles = 2, pinned = { setOf("aic-1") })
        for ((age, id) in listOf("1", "2", "3", "4").withIndex()) {
            cache.write("aic-$id", artwork(id))
            File(folder.root, "aic-$id.json").setLastModified(1_000_000L * (age + 1)) // 1 oldest, 4 newest
        }
        cache.write("aic-5", artwork("5"))
        val kept = folder.root.list()!!.filter { it.endsWith(".json") }.sorted()
        assertEquals(listOf("aic-1.json", "aic-4.json", "aic-5.json"), kept)
    }
}
