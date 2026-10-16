package com.dailygallery.app.data

import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.Credit
import com.dailygallery.app.domain.Paragraph
import com.dailygallery.app.domain.Passage
import kotlinx.serialization.Serializable
import java.net.URLEncoder

/** A Wikipedia article as its opening paragraphs and its sections, each sorted by what it's about. */
data class Article(val title: String, val url: String, val lead: List<Paragraph>, val sections: List<Section>)

data class Section(val heading: String, val topic: Topic, val paragraphs: List<Paragraph>, val url: String)

enum class Topic { STORY, MATERIALS, HISTORY, CRITICS, SKIP }

/** English Wikipedia, read as plain text through the MediaWiki API (text CC BY-SA 4.0). */
class WikipediaSource(private val http: HttpGet, private val api: String = "https://en.wikipedia.org/w/api.php") {

    /** The article, or null when Wikipedia has none by that title. */
    suspend fun article(title: String): Article? {
        val query = "action=query&prop=extracts&explaintext=1&exsectionformat=wiki&redirects=1" +
            "&format=json&formatversion=2&titles=" + URLEncoder.encode(title, "UTF-8")
        val page = sourceJson.decodeFromString<WikiResponse>(http.get("$api?$query")).query?.pages?.firstOrNull()
        if (page == null || page.missing || page.extract.isNullOrBlank()) return null
        return parse(page.title, page.extract)
    }

    companion object {
        private val heading = Regex("""^(={2,})\s*(.+?)\s*=+\s*$""")

        fun pageUrl(title: String): String =
            "https://en.wikipedia.org/wiki/" + URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")

        /** Plain-text extract: "== Heading ==" opens a section, "=== Sub ===" a subheading inside it. */
        fun parse(title: String, extract: String): Article {
            val url = pageUrl(title)
            val lead = mutableListOf<Paragraph>()
            val sections = mutableListOf<Section>()
            var current: Pair<String, MutableList<Paragraph>>? = null

            fun close() {
                current?.let { (name, paragraphs) ->
                    // Drop subheadings left with nothing under them (lists and tables are not in the extract).
                    val kept = paragraphs.filterIndexed { i, p -> !p.subheading || paragraphs.getOrNull(i + 1)?.subheading == false }
                    if (kept.isNotEmpty()) sections += Section(name, topicOf(name), kept, "$url#" + name.replace(' ', '_'))
                }
            }

            for (line in extract.lines()) {
                val match = heading.find(line)
                val text = line.trim()
                when {
                    match != null && match.groupValues[1].length == 2 -> {
                        close()
                        current = match.groupValues[2] to mutableListOf()
                    }
                    match != null -> current?.second?.add(Paragraph(match.groupValues[2], subheading = true))
                    text.isEmpty() -> Unit
                    current == null -> lead += Paragraph(text)
                    else -> current!!.second += Paragraph(text)
                }
            }
            close()
            return Article(title, url, lead, sections)
        }

        private val skip = listOf(
            "references", "see also", "external links", "notes", "bibliography", "further reading", "sources",
            "citations", "footnotes", "gallery", "media", "works cited", "explanatory notes", "selected works",
        )
        private val critics = listOf(
            "reception", "critic", "interpretation", "analysis", "legacy", "significance", "influence",
            "reputation", "assessment", "controvers", "appraisal", "symbolism", "debate",
        )
        private val materials = listOf(
            "material", "technique", "craftsmanship", "preparation", "conservation", "restoration", "condition",
            "pigment", "medium", "construction", "technical",
        )
        private val printHeadings = setOf("states", "impressions", "surviving impressions") // prints, not places
        private val history = listOf(
            "provenance", "ownership", "owner", "acquisition", "exhibition", "sale", "history", "theft",
            "dating", "attribution", "commission", "discovery", "rediscovery",
        )

        /** Sorts a section by its heading; the first matching list wins, and anything else is part of the story. */
        fun topicOf(heading: String): Topic {
            val name = heading.lowercase()
            return when {
                skip.any { name == it || name.startsWith("$it ") } -> Topic.SKIP
                critics.any { it in name } -> Topic.CRITICS
                materials.any { it in name } || name in printHeadings -> Topic.MATERIALS
                history.any { it in name } -> Topic.HISTORY
                else -> Topic.STORY
            }
        }
    }
}

/**
 * Adds what Wikipedia says to a museum record: the article on the work (story, materials, history,
 * critics) and, from the article on the artist, what critics said about them and, when the museum
 * has no biography, the article's opening.
 */
fun Artwork.withWikipedia(work: Article?, artist: Article?): Artwork {
    fun Section.passage() = Passage(heading, "Wikipedia", paragraphs, url)
    fun topic(topic: Topic) = work?.sections.orEmpty().filter { it.topic == topic }.map { it.passage() }

    val workLead = work?.lead?.takeIf { it.isNotEmpty() }?.let { Passage("", "Wikipedia", it, work.url) }
    return copy(
        story = story + listOfNotNull(workLead) + topic(Topic.STORY),
        materials = materials + topic(Topic.MATERIALS),
        history = history + topic(Topic.HISTORY),
        critics = critics + topic(Topic.CRITICS),
        artistCritics = artist?.sections.orEmpty().filter { it.topic == Topic.CRITICS }.map { it.passage() },
        artistBio = artistBio ?: artist?.lead?.takeIf { it.isNotEmpty() }?.let { Passage(artist.title, "Wikipedia", it, artist.url) },
        credits = credits + listOfNotNull(work, artist).map { Credit("Text from Wikipedia, “${it.title}”, CC BY-SA 4.0", it.url) },
    )
}

@Serializable
internal class WikiResponse(val query: WikiQuery? = null)

@Serializable
internal class WikiQuery(val pages: List<WikiPage> = emptyList())

@Serializable
internal class WikiPage(val title: String = "", val extract: String? = null, val missing: Boolean = false)
