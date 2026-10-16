package com.dailygallery.app.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved Wikipedia articles on A Sunday Afternoon on the Island of La Grande Jatte and on Seurat. */
class WikipediaSourceTest {
    private val api = "https://en.wikipedia.org/w/api.php"
    private val http = FakeHttp(
        "$api?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles=A+Sunday" to
            { fixture("wikipedia-grande-jatte.json") },
        "$api?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles=Georges+Seurat" to
            { fixture("wikipedia-seurat.json") },
        "$api?action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1&format=json&formatversion=2&titles=No+such" to
            { """{"query": {"pages": [{"title": "No such page", "missing": true}]}}""" },
    )
    private val wikipedia = WikipediaSource(http)
    private val painting = runBlocking { wikipedia.article("A Sunday Afternoon on the Island of La Grande Jatte") }!!
    private val seurat = runBlocking { wikipedia.article("Georges Seurat") }!!

    @Test
    fun theArticleOpensWithItsLeadAndLinksToThePage() {
        assertEquals("A Sunday Afternoon on the Island of La Grande Jatte", painting.title)
        assertEquals("https://en.wikipedia.org/wiki/A_Sunday_Afternoon_on_the_Island_of_La_Grande_Jatte", painting.url)
        assertTrue(painting.lead.first().text.startsWith("A Sunday Afternoon on the Island of La Grande Jatte (French: Un dimanche"))
    }

    @Test
    fun eachSectionIsSortedByWhatItIsAbout() {
        assertEquals(
            listOf(
                "Background" to Topic.STORY,
                "Interpretation" to Topic.CRITICS,
                "Painting materials" to Topic.MATERIALS,
                "Acquisition by the Art Institute of Chicago" to Topic.HISTORY,
                "In popular culture" to Topic.STORY,
            ),
            painting.sections.filter { it.topic != Topic.SKIP }.map { it.heading to it.topic },
        )
        // "Related works by Seurat" is a gallery of images: nothing in the text, so no section.
        assertNull(painting.sections.firstOrNull { it.heading == "Related works by Seurat" })
        assertEquals(
            "https://en.wikipedia.org/wiki/A_Sunday_Afternoon_on_the_Island_of_La_Grande_Jatte#Painting_materials",
            painting.sections.first { it.heading == "Painting materials" }.url,
        )
    }

    @Test
    fun subheadingsStayInsideTheirSection() {
        val biography = seurat.sections.first { it.heading == "Biography" }
        assertEquals("Family and education", biography.paragraphs.first().text)
        assertTrue(biography.paragraphs.first().subheading)
        assertTrue(biography.paragraphs.count { it.subheading } >= 4)
        assertEquals(Topic.CRITICS, seurat.sections.first { it.heading == "Influence" }.topic)
    }

    @Test
    fun aMissingArticleIsNull() {
        assertNull(runBlocking { wikipedia.article("No such page") })
    }

    @Test
    fun headingsAreSortedByTheirWords() {
        val cases = mapOf(
            "Critical reception" to Topic.CRITICS,
            "Significance and legacy" to Topic.CRITICS,
            "Context and reception" to Topic.CRITICS,
            "Technique" to Topic.MATERIALS,
            "States" to Topic.MATERIALS,
            "Life in the United States" to Topic.STORY,
            "Provenance" to Topic.HISTORY,
            "Exhibition history" to Topic.HISTORY,
            "Description" to Topic.STORY,
            "References" to Topic.SKIP,
            "Notes" to Topic.SKIP,
            "Further reading" to Topic.SKIP,
        )
        for ((heading, topic) in cases) assertEquals(heading, topic, WikipediaSource.topicOf(heading))
    }

    @Test
    fun pageUrlsEncodeAccentsAndUseUnderscores() {
        assertEquals("https://en.wikipedia.org/wiki/Bathers_at_Asni%C3%A8res", WikipediaSource.pageUrl("Bathers at Asnières"))
    }

    // Merging into the museum's record.

    private val museum = runBlocking {
        AicSource(
            FakeHttp(
                "https://api.artic.edu/api/v1/artworks/27992" to { fixture("aic-artwork-27992.json") },
                "https://api.artic.edu/api/v1/agents/40810" to { fixture("aic-agent-40810.json") },
            ),
        ).artwork("27992")
    }

    @Test
    fun wikipediaFollowsTheMuseumInEachPart() {
        val artwork = museum.withWikipedia(painting, seurat)
        assertEquals(
            listOf("Art Institute of Chicago", "Wikipedia", "Wikipedia", "Wikipedia"),
            artwork.story.map { it.source },
        )
        assertEquals(listOf("", "", "Background", "In popular culture"), artwork.story.map { it.title })
        assertEquals(listOf("Interpretation"), artwork.critics.map { it.title })
        assertEquals(listOf("Painting materials"), artwork.materials.map { it.title })
        assertEquals(listOf("Acquisition by the Art Institute of Chicago"), artwork.history.map { it.title })
        assertEquals(listOf("Influence"), artwork.artistCritics.map { it.title })
        assertEquals(museum.artistBio, artwork.artistBio) // the museum's biography comes first
        assertEquals(
            listOf(
                "Text from Wikipedia, “A Sunday Afternoon on the Island of La Grande Jatte”, CC BY-SA 4.0",
                "Text from Wikipedia, “Georges Seurat”, CC BY-SA 4.0",
            ),
            artwork.credits.drop(museum.credits.size).map { it.text },
        )
    }

    @Test
    fun withoutAMuseumBiographyTheArtistsArticleIntroducesThem() {
        val artwork = museum.copy(artistBio = null).withWikipedia(work = null, artist = seurat)
        assertEquals("Georges Seurat", artwork.artistBio!!.title)
        assertEquals("Wikipedia", artwork.artistBio!!.source)
        assertEquals(museum.story, artwork.story)
    }
}
