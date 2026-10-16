package com.dailygallery.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextTest {
    private fun Paragraph.italicText() = italics.map { text.substring(it.start, it.end) }

    @Test
    fun museumHtmlBecomesParagraphsWithTheTitlesStillInItalics() {
        val html = "<p>In <em>Ferris Bueller’s Day Off</em>, Cameron studies this painting.</p>\n" +
            "<p>Seurat called it <i>Un dimanche</i>.</p>\n"
        val paragraphs = htmlParagraphs(html)
        assertEquals(
            listOf("In Ferris Bueller’s Day Off, Cameron studies this painting.", "Seurat called it Un dimanche."),
            paragraphs.map { it.text },
        )
        assertEquals(listOf("Ferris Bueller’s Day Off"), paragraphs[0].italicText())
        assertEquals(listOf("Un dimanche"), paragraphs[1].italicText())
    }

    @Test
    fun entitiesAreDecodedAndLinksKeepTheirText() {
        val paragraph = htmlParagraphs("Tom &amp; Jerry &#8212; &quot;<a href=\"/x\">a link</a>&quot; &#x2019;s").single()
        assertEquals("Tom & Jerry — \"a link\" ’s", paragraph.text)
    }

    @Test
    fun whitespaceIsClosedUpAndPlainTextSplitsAtBlankLines() {
        val paragraphs = htmlParagraphs("  First   line\r\ncontinues.  \r\n\r\n  Second.\n")
        assertEquals(listOf("First line continues.", "Second."), paragraphs.map { it.text })
    }

    @Test
    fun anItalicRunLeftOpenEndsWithItsParagraph() {
        val paragraphs = htmlParagraphs("<p>A <em>long title</p><p>goes on</em> here</p>")
        assertEquals(listOf("long title"), paragraphs[0].italicText())
        assertEquals(listOf("goes on"), paragraphs[1].italicText())
    }

    @Test
    fun emptyMarkupGivesNoParagraphs() {
        assertEquals(emptyList<Paragraph>(), htmlParagraphs("<p> </p><br/>"))
        assertNull(htmlLine(""))
    }

    @Test
    fun aLineWithBreaksBecomesOneParagraph() {
        val line = htmlLine("<em>Claude Monet</em>.<br>Musée de l'Orangerie (1931).")!!
        assertEquals("Claude Monet. Musée de l'Orangerie (1931).", line.text)
        assertEquals(listOf("Claude Monet"), line.italicText())
    }

    @Test
    fun ownersSplitAtSemicolonsButNotInsideBrackets() {
        val provenance = "Mme. Seurat (died 1899; the artist’s mother), Paris, 1891; sold to Casimir Brû, 1900 [see note; 2]; given to the Art Institute, 1926."
        assertEquals(
            listOf(
                "Mme. Seurat (died 1899; the artist’s mother), Paris, 1891",
                "sold to Casimir Brû, 1900 [see note; 2]",
                "given to the Art Institute, 1926.",
            ),
            splitOutsideBrackets(provenance),
        )
    }

    @Test
    fun blocksAreSeparatedByBlankLines() {
        assertEquals(listOf("Paris, 1886.", "Brussels, 1887."), blocks("Paris, 1886.\n\n Brussels, 1887.\n\n\n"))
    }

    @Test
    fun theFirstYearIsFoundButHouseAndCatalogueNumbersAreNot() {
        assertEquals(1886, firstYear("Paris, Maison Dorée, 1, rue Laffitte, May 15–June 15, 1886, cat. 175."))
        assertEquals(1891, firstYear("by descent to Mme. Seurat, by 1891"))
        assertNull(firstYear("Paris, 23 Boulevard des Italiens, cat. 353"))
    }

    @Test
    fun anOwnersYearIsTheLastOneOutsideBrackets() {
        assertEquals(1891, ownerYear("By descent to Mme. Seurat, the artist’s mother (died 1899), Paris, 1891"))
        assertEquals(1924, ownerYear("Sold for $20,000 [1917 price] to Frederic Clay Bartlett, Chicago, 1924"))
        assertEquals(1899, ownerYear("Lucie Brû (died 1899)")) // only a year in brackets: better than none
        assertNull(ownerYear("Lucie Brû Cousturier and Edmond Cousturier, Paris"))
    }

    @Test
    fun eventsSortOldestFirstWithUndatedOnesLast() {
        val events = listOf(Event(2004, Paragraph("b")), Event(null, Paragraph("u")), Event(1922, Paragraph("a")))
        assertEquals(listOf("a", "b", "u"), events.chronological().map { it.text.text })
    }
}
