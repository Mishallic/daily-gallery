package com.dailygallery.app.data

import com.dailygallery.app.domain.Fact
import com.dailygallery.app.domain.Source
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Art Institute's record of A Sunday on La Grande Jatte, as saved from its API. */
class AicSourceTest {
    private val api = "https://api.artic.edu/api/v1"
    private val http = FakeHttp(
        "$api/artworks/27992" to { fixture("aic-artwork-27992.json") },
        "$api/agents/40810" to { fixture("aic-agent-40810.json") },
    )
    private val artwork = runBlocking { AicSource(http).artwork("27992") }

    @Test
    fun readsTheTitleArtistAndDate() {
        assertEquals(Source.AIC, artwork.source)
        assertEquals("A Sunday on La Grande Jatte — 1884", artwork.title)
        assertEquals("Georges Seurat", artwork.artist)
        assertEquals("French, 1859–1891", artwork.artistDetails)
        assertEquals("1884–86, border added 1888–89", artwork.date)
        assertEquals(listOf("Painting", "France", "Pointillism"), artwork.tags)
        assertEquals("https://www.artic.edu/artworks/27992", artwork.pageUrl)
    }

    @Test
    fun theImageComesFromTheMuseumsIiifServerWithItsDescription() {
        val image = artwork.image!!
        assertTrue(image.url.endsWith("/full/843,/0/default.jpg"))
        assertTrue(image.largeUrl.endsWith("/full/1686,/0/default.jpg"))
        assertEquals(9310, image.width)
        assertEquals("Large painting of people in a crowded park, brushstrokes are dots.", image.altText)
    }

    @Test
    fun theStoryIsTheMuseumsDescriptionWithTitlesInItalics() {
        val passage = artwork.story.single()
        assertEquals("Art Institute of Chicago", passage.source)
        val first = passage.paragraphs.first()
        assertTrue(first.text.startsWith("In Ferris Bueller’s Day Off, Ferris’s best friend Cameron Frye"))
        assertEquals("Ferris Bueller’s Day Off", first.text.substring(first.italics[0].start, first.italics[0].end))
    }

    @Test
    fun factsListTheMediumAndOnlyTheSpecificMaterials() {
        val facts = artwork.facts.associate { it.label to it.value }
        assertEquals("Oil on canvas", facts["Medium"])
        assertEquals("Oil paint, canvas", facts["Materials"])
        assertEquals("Oil painting", facts["Technique"])
        assertEquals("207.5 × 308.1 cm (81 3/4 × 121 1/4 in.)", facts["Dimensions"])
        assertEquals("Gallery 240, Art Institute of Chicago", facts["On view"])
        assertTrue(Fact("Object number", "1926.224") in artwork.facts)
    }

    @Test
    fun ownersAreListedInOrderWithTheirYears() {
        assertEquals(7, artwork.owners.size)
        assertEquals("By descent to Mme. Seurat, the artist’s mother (died 1899), Paris, 1891", artwork.owners.first().text.text)
        assertEquals(1891, artwork.owners.first().year) // not 1899, the year in brackets she died
        assertEquals("Given to the Art Institute, 1926.", artwork.owners.last().text.text)
        assertEquals(1926, artwork.owners.last().year)
    }

    @Test
    fun exhibitionsAndPublicationsStartWithTheEarliest() {
        assertEquals(1886, artwork.exhibitions.first().year)
        assertTrue(artwork.exhibitions.first().text.text.startsWith("Paris, Maison Dorée"))
        assertEquals("Jules Christophe, Georges Seurat (Paris, 1890).", artwork.publications.first().text.text)
        assertTrue(artwork.publications.size > 50)
    }

    @Test
    fun theArtistsBiographyComesFromTheMuseum() {
        val bio = artwork.artistBio!!
        assertEquals("Georges Seurat", bio.title)
        assertTrue(bio.paragraphs.first().text.startsWith("Inspired by recently published research in optical and color theory"))
    }

    @Test
    fun withoutTheArtistRecordTheArtworkStillLoads() {
        val noAgent = FakeHttp("$api/artworks/27992" to { fixture("aic-artwork-27992.json") })
        val artwork = runBlocking { AicSource(noAgent).artwork("27992") }
        assertNull(artwork.artistBio)
        assertEquals("Georges Seurat", artwork.artist)
    }

    @Test
    fun anUnattributedWorkIsCreditedToItsPlace() {
        val record = """{"data": {"id": 1, "title": "Vessel", "artist_display": "Ancient Greek\nAttica",
            "artist_title": null, "place_of_origin": "Athens", "material_titles": null}}"""
        val artwork = runBlocking { AicSource(FakeHttp("$api/artworks/1" to { record })).artwork("1") }
        assertEquals("Ancient Greek", artwork.artist)
        assertEquals("Athens", artwork.artistDetails)
        assertNull(artwork.image)
        assertEquals(emptyList<Any>(), artwork.story)
    }

    @Test
    fun cleanTermsKeepsTheSpecificOnes() {
        assertEquals(
            listOf("oil paint", "canvas"),
            AicSource.cleanTerms(listOf("oil paint (paint)", "painting", "paint", "canvas"), type = "painting"),
        )
        assertEquals(
            listOf("oil painting"),
            AicSource.cleanTerms(listOf("oil painting", "painting techniques", "painting", "painting (image making)"), "painting"),
        )
    }
}
