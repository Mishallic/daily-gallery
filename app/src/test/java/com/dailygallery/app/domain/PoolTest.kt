package com.dailygallery.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class PoolTest {
    private fun entry(id: String, artist: String = "Artist $id") =
        PoolEntry(Source.AIC, id, "Title $id", artist, "1900", "https://example.org/$id.jpg")

    private val start = LocalDate.of(2026, 10, 16)
    private val pool = Pool(start, listOf(entry("a"), entry("b"), entry("c")))

    @Test
    fun theStartDayShowsTheFirstEntryAndEachDayTheNext() {
        assertEquals("a", pool.entryFor(start).id)
        assertEquals("b", pool.entryFor(start.plusDays(1)).id)
        assertEquals("c", pool.entryFor(start.plusDays(2)).id)
    }

    @Test
    fun afterTheLastEntryTheScheduleStartsAgain() {
        assertEquals("a", pool.entryFor(start.plusDays(3)).id)
        assertEquals("b", pool.entryFor(start.plusDays(301)).id)
    }

    @Test
    fun daysBeforeTheStartCountBackFromTheEnd() {
        assertEquals("c", pool.entryFor(start.minusDays(1)).id)
        assertEquals("a", pool.entryFor(start.minusDays(3)).id)
    }

    @Test
    fun daysUpToListsTodayFirstAndNeverRepeatsAnArtwork() {
        val days = pool.daysUpTo(start.plusDays(1), count = 10)
        assertEquals(listOf(start.plusDays(1), start, start.minusDays(1)), days.map { it.date })
        assertEquals(listOf("b", "a", "c"), days.map { it.entry.id })
    }

    // The schedule that ships in the app.

    private val shipped = Pool.parse(File("src/main/assets/pool.json").readText())

    @Test
    fun theShippedPoolCoversMoreThanAYearFromBothMuseums() {
        assertTrue(shipped.entries.size >= 400)
        assertEquals(setOf(Source.AIC, Source.CMA), shipped.entries.map { it.source }.toSet())
        assertEquals(LocalDate.of(2026, 10, 16), shipped.start)
    }

    @Test
    fun everyShippedArtworkIsListedOnceWithATitleAndAnHttpsThumbnail() {
        assertEquals(shipped.entries.size, shipped.entries.map { it.key }.toSet().size)
        for (entry in shipped.entries) {
            assertTrue(entry.key, entry.title.isNotBlank() && entry.artist.isNotBlank())
            assertTrue(entry.key, entry.thumbnail.startsWith("https://"))
        }
    }

    @Test
    fun theSameArtistNeverComesBackWithinTwoWeeks() {
        val lastSeen = mutableMapOf<String, Int>()
        shipped.entries.forEachIndexed { day, entry ->
            lastSeen[entry.artist]?.let { previous ->
                assertTrue("${entry.artist} on days $previous and $day", day - previous >= 14)
            }
            lastSeen[entry.artist] = day
        }
    }
}
