package com.dailygallery.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** One artwork in the schedule, with enough to show it in a list before its full record is read. */
@Serializable
data class PoolEntry(
    val source: Source,
    val id: String,
    val title: String,
    val artist: String,
    val date: String,
    val thumbnail: String,
    /** English Wikipedia article on the artwork, where Wikidata links one. */
    val wikipedia: String? = null,
    /** English Wikipedia article on the artist. */
    val artistWikipedia: String? = null,
) {
    val key: String get() = "${source.name.lowercase()}-$id"
}

data class Day(val date: LocalDate, val entry: PoolEntry)

/**
 * The schedule of daily artworks, built by scripts/build_pool.py. Day N after [start] shows entry N;
 * after the last entry the schedule starts again from the top. Every phone shows the same artwork
 * on the same calendar day, by its own local date.
 */
class Pool(val start: LocalDate, val entries: List<PoolEntry>) {
    init {
        require(entries.isNotEmpty()) { "The pool has no artworks" }
    }

    fun entryFor(day: LocalDate): PoolEntry {
        val index = Math.floorMod(ChronoUnit.DAYS.between(start, day), entries.size.toLong())
        return entries[index.toInt()]
    }

    /** [today] and the days before it, newest first; never more days than artworks, so none repeats. */
    fun daysUpTo(today: LocalDate, count: Int = entries.size): List<Day> =
        (0 until minOf(count, entries.size)).map { back ->
            val day = today.minusDays(back.toLong())
            Day(day, entryFor(day))
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): Pool {
            val file = json.decodeFromString<PoolFile>(text)
            return Pool(LocalDate.parse(file.start), file.entries)
        }
    }
}

@Serializable
private class PoolFile(val start: String, val entries: List<PoolEntry>)
