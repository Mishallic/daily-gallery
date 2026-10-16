package com.dailygallery.app.data

import com.dailygallery.app.domain.Artwork
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

/**
 * Artworks already put together, one JSON file each, so a day opens instantly and offline.
 * Keeps the [maxFiles] most recently opened, and always the [pinned] ones (saved artworks).
 */
class ArtworkCache(
    private val dir: File,
    private val maxFiles: Int = 60,
    private val pinned: () -> Set<String> = { emptySet() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    fun read(key: String): Artwork? {
        val file = file(key)
        if (!file.exists()) return null
        return try {
            json.decodeFromString<Artwork>(file.readText()).also { file.setLastModified(System.currentTimeMillis()) }
        } catch (_: SerializationException) {
            file.delete() // written by an older version, or cut short: read it again from the source
            null
        } catch (_: IllegalArgumentException) {
            file.delete()
            null
        } catch (_: IOException) {
            null
        }
    }

    @Synchronized
    fun write(key: String, artwork: Artwork) {
        dir.mkdirs()
        val temporary = File(dir, "$key.json.tmp")
        temporary.writeText(json.encodeToString(Artwork.serializer(), artwork))
        temporary.renameTo(file(key).also { it.delete() })
        prune()
    }

    private fun prune() {
        val keep = pinned()
        val files = dir.listFiles { file -> file.name.endsWith(".json") }.orEmpty()
            .filter { it.name.removeSuffix(".json") !in keep }
            .sortedByDescending { it.lastModified() }
        files.drop(maxFiles).forEach { it.delete() }
    }

    private fun file(key: String) = File(dir, "$key.json")
}
