package com.dailygallery.app.data

import com.dailygallery.app.domain.PoolEntry
import com.dailygallery.app.domain.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SavedStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun entry(id: String) = PoolEntry(Source.CMA, id, "Title $id", "Artist", "1900", "https://example.org/$id.jpg")
    private val file get() = File(folder.root, "saved.json")
    // Writes run right away, so each test can read the file back.
    private fun store() = SavedStore(file, CoroutineScope(Dispatchers.Unconfined), writes = Dispatchers.Unconfined)

    @Test
    fun savingPutsTheNewestFirstAndSavingAgainForgets() {
        val saved = store()
        saved.toggle(entry("1"))
        saved.toggle(entry("2"))
        assertEquals(listOf("2", "1"), saved.entries.value.map { it.id })
        assertEquals(setOf("cma-1", "cma-2"), saved.keys())

        saved.toggle(entry("1"))
        assertEquals(listOf("2"), saved.entries.value.map { it.id })
    }

    @Test
    fun savedArtworksAreThereNextTime() {
        store().apply {
            toggle(entry("1"))
            toggle(entry("2"))
        }
        assertEquals(listOf(entry("2"), entry("1")), store().entries.value)
    }

    @Test
    fun anUnreadableFileStartsAnEmptyList() {
        file.writeText("[{broken")
        assertEquals(emptyList<PoolEntry>(), store().entries.value)
    }
}
