package com.dailygallery.app.data

import com.dailygallery.app.domain.PoolEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * The artworks the user saved, newest first, in one small JSON file. The list changes at once;
 * the file is written in the background, one write at a time, each with the latest list.
 */
class SavedStore(
    private val file: File,
    private val scope: CoroutineScope,
    private val writes: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _entries = MutableStateFlow(read())
    val entries: StateFlow<List<PoolEntry>> = _entries

    fun keys(): Set<String> = _entries.value.mapTo(HashSet()) { it.key }

    /** Saves [entry], or forgets it if it was saved. */
    fun toggle(entry: PoolEntry) {
        val now = _entries.updateAndGet { saved ->
            if (saved.any { it.key == entry.key }) saved.filterNot { it.key == entry.key } else listOf(entry) + saved
        }
        scope.launch(writes) { write(now) }
    }

    private fun read(): List<PoolEntry> = try {
        if (file.exists()) json.decodeFromString<List<PoolEntry>>(file.readText()) else emptyList()
    } catch (_: SerializationException) {
        emptyList()
    } catch (_: IllegalArgumentException) {
        emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    private fun write(entries: List<PoolEntry>) {
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        temporary.writeText(json.encodeToString(entries))
        file.delete()
        temporary.renameTo(file)
    }
}
