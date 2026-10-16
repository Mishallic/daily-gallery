package com.dailygallery.app.data

import com.dailygallery.app.domain.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved Cleveland records: Monet's The Red Kerchief, the Cleveland Apollo, and a Chola bronze with no named maker. */
class ClevelandSourceTest {
    private val api = "https://openaccess-api.clevelandart.org/api"
    private val http = FakeHttp(
        "$api/artworks/135382" to { fixture("cma-artwork-135382.json") },
        "$api/artworks/163448" to { fixture("cma-artwork-163448.json") },
        "$api/artworks/111811" to { fixture("cma-artwork-111811.json") },
    )
    private fun read(id: String) = runBlocking { ClevelandSource(http).artwork(id) }

    private val kerchief = read("135382")

    @Test
    fun readsTheTitleArtistAndDate() {
        assertEquals(Source.CMA, kerchief.source)
        assertEquals("The Red Kerchief", kerchief.title)
        assertEquals("Claude Monet", kerchief.artist)
        assertEquals("French, 1840–1926", kerchief.artistDetails)
        assertEquals("c. 1868–73", kerchief.date)
        assertEquals(listOf("Painting", "France, 19th century"), kerchief.tags)
        assertEquals("https://clevelandart.org/art/1958.39", kerchief.pageUrl)
    }

    @Test
    fun theImageIsTheWebCopyWithThePrintCopyForZooming() {
        val image = kerchief.image!!
        assertEquals("https://openaccess-cdn.clevelandart.org/1958.39/1958.39_web.jpg", image.url)
        assertEquals("https://openaccess-cdn.clevelandart.org/1958.39/1958.39_print.jpg", image.largeUrl)
        assertEquals(723, image.width)
        assertEquals(900, image.height)
    }

    @Test
    fun theStoryIsTheDescriptionThenDidYouKnow() {
        assertEquals(listOf("", "Did you know?"), kerchief.story.map { it.title })
        assertTrue(kerchief.story[0].paragraphs[0].text.startsWith("This painting depicts Monet's first wife, Camille"))
        assertTrue(kerchief.story[1].paragraphs[0].text.startsWith("Madame Monet’s red cape"))
    }

    @Test
    fun factsNameTheOriginalTitleMediumAndGallery() {
        val facts = kerchief.facts.associate { it.label to it.value }
        assertEquals("Femme à la capeline rouge", facts["Original title"])
        assertEquals("Oil on fabric", facts["Medium"])
        assertEquals("Not signed", facts["Inscriptions"])
        assertEquals("Bequest of Leonard C. Hanna Jr.", facts["Credit line"])
        assertEquals("1958.39", facts["Object number"])
        assertEquals("Gallery 222 (Impressionism & Post-Impressionism), Cleveland Museum of Art", facts["On view"])
    }

    @Test
    fun ownersRunFromMonetsSonToTheMuseumWithTheMuseumsDates() {
        assertEquals(listOf(1931, 1939, 1948, 1948, 1958), kerchief.owners.map { it.year })
        assertTrue(kerchief.owners.first().text.text.startsWith("Michel Monet [1878-1966] Giverny"))
        assertEquals("The Cleveland Museum of Art, Cleveland, OH", kerchief.owners.last().text.text)
    }

    @Test
    fun exhibitionsAndPublicationsAreSortedOldestFirst() {
        assertEquals(18, kerchief.exhibitions.size)
        assertEquals(1931, kerchief.exhibitions.first().year)
        assertEquals(kerchief.exhibitions.mapNotNull { it.year }.sorted(), kerchief.exhibitions.mapNotNull { it.year })
        assertEquals(82, kerchief.publications.size)
        val years = kerchief.publications.mapNotNull { it.year }
        assertEquals(years.sorted(), years)
        val geffroy = kerchief.publications.first { it.text.text.startsWith("Geffroy") }
        assertEquals("Claude Monet, sa vie, son œuvre", geffroy.text.text.substring(geffroy.text.italics[0].start, geffroy.text.italics[0].end))
    }

    @Test
    fun aConservationStatementGoesUnderMaterialsAndBareDatesAreNoBiography() {
        val apollo = read("163448")
        assertEquals("Praxiteles", apollo.artist)
        assertEquals("Attributed to · Greek, Athenian, c. 400–330 BCE", apollo.artistDetails)
        assertNull(apollo.artistBio) // the museum's "biography" is only "c. 400/390-330/325 BC"
        assertEquals("Conservation", apollo.materials.single().title)
        assertTrue(apollo.materials.single().paragraphs[0].text.startsWith("The surviving bronze sculpture comprises"))
        assertNull(apollo.facts.firstOrNull { it.label == "On view" })
    }

    @Test
    fun aWorkWithNoNamedMakerIsCreditedToItsCulture() {
        val nataraja = read("111811")
        assertEquals("South India, Tamil Nadu, Chola period (900–1200s)", nataraja.artist)
        assertNull(nataraja.artistDetails)
    }
}
