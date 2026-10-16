package com.dailygallery.app.data

import com.dailygallery.app.domain.ArtImage
import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.Credit
import com.dailygallery.app.domain.Event
import com.dailygallery.app.domain.Fact
import com.dailygallery.app.domain.Paragraph
import com.dailygallery.app.domain.Passage
import com.dailygallery.app.domain.Source
import com.dailygallery.app.domain.blocks
import com.dailygallery.app.domain.firstYear
import com.dailygallery.app.domain.htmlLine
import com.dailygallery.app.domain.htmlParagraphs
import com.dailygallery.app.domain.ownerYear
import com.dailygallery.app.domain.splitOutsideBrackets
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The Art Institute of Chicago's public API (api.artic.edu): no key; images of public-domain works
 * are CC0 and the descriptions CC BY 4.0. Reads the artwork, then its artist.
 */
class AicSource(private val http: HttpGet, private val api: String = "https://api.artic.edu/api/v1") {

    suspend fun artwork(id: String): Artwork {
        val record = sourceJson.decodeFromString<AicResponse<AicArtwork>>(http.get("$api/artworks/$id?fields=$FIELDS")).data
        val artist = record.artistId?.let { artistId ->
            optional {
                sourceJson.decodeFromString<AicResponse<AicAgent>>(
                    http.get("$api/agents/$artistId?fields=id,title,birth_date,death_date,description"),
                ).data
            }
        }
        return toArtwork(record, artist)
    }

    private fun toArtwork(work: AicArtwork, artist: AicAgent?): Artwork {
        val museum = Source.AIC.museum
        val pageUrl = "https://www.artic.edu/artworks/${work.id}"
        val displayLines = work.artistDisplay.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        // "Georges Seurat (French, 1859–1891)", or the name and details on two lines.
        val bracketed = displayLines.firstOrNull()?.let { Regex("""\((.+)\)$""").find(it)?.groupValues?.get(1) }
        val artistName = work.artistTitle ?: displayLines.firstOrNull()?.substringBefore(" (") ?: "Unknown artist"
        val artistDetails = if (work.artistTitle == null) work.placeOfOrigin else bracketed ?: displayLines.getOrNull(1)

        val story = (work.description ?: work.shortDescription)?.let { htmlParagraphs(it) }.orEmpty()
        val type = work.type?.lowercase()
        val facts = listOfNotNull(
            work.medium?.let { Fact("Medium", it) },
            cleanTerms(work.materials, type).takeIf { it.isNotEmpty() }?.let { Fact("Materials", termList(it)) },
            cleanTerms(work.techniques, type).takeIf { it.isNotEmpty() }?.let { Fact("Technique", termList(it)) },
            work.dimensions?.let { Fact("Dimensions", it) },
            work.inscriptions?.let { Fact("Inscriptions", it) },
            work.creditLine?.let { Fact("Credit line", it) },
            work.referenceNumber?.let { Fact("Object number", it) },
            work.gallery?.takeIf { work.onView }?.let { Fact("On view", "$it, $museum") },
        )

        return Artwork(
            source = Source.AIC,
            id = work.id.toString(),
            title = work.title,
            artist = artistName,
            artistDetails = artistDetails,
            date = work.dateDisplay.orEmpty(),
            image = work.imageId?.let { imageId ->
                ArtImage(
                    url = "$IIIF/$imageId/full/843,/0/default.jpg",
                    largeUrl = "$IIIF/$imageId/full/1686,/0/default.jpg",
                    width = work.thumbnail?.width,
                    height = work.thumbnail?.height,
                    altText = work.thumbnail?.altText,
                )
            },
            pageUrl = pageUrl,
            tags = listOfNotNull(work.type, work.placeOfOrigin, work.style).distinct(),
            story = if (story.isEmpty()) emptyList() else listOf(Passage("", museum, story, pageUrl)),
            facts = facts,
            owners = work.provenance?.let { text ->
                splitOutsideBrackets(text).map { owner -> Event(ownerYear(owner), Paragraph(owner.replaceFirstChar { it.uppercase() })) }
            }.orEmpty(),
            exhibitions = work.exhibitions?.let { events(it) }.orEmpty(),
            publications = work.publications?.let { events(it) }.orEmpty(),
            artistBio = artist?.description?.let { bio ->
                Passage(artist.title, museum, htmlParagraphs(bio), "https://www.artic.edu/artists/${artist.id}")
            },
            credits = listOf(
                Credit("Image and record: $museum, CC0", pageUrl),
                Credit("Museum text: $museum, CC BY 4.0", "https://creativecommons.org/licenses/by/4.0/"),
            ),
        )
    }

    private fun termList(terms: List<String>) = terms.joinToString(", ").replaceFirstChar { it.uppercase() }

    private fun events(text: String): List<Event> =
        blocks(text).mapNotNull { block -> htmlLine(block)?.let { Event(firstYear(it.text), it) } }

    companion object {
        private const val IIIF = "https://www.artic.edu/iiif/2"
        private const val FIELDS = "id,title,artist_display,artist_title,artist_id,date_display,place_of_origin," +
            "medium_display,dimensions,material_titles,technique_titles,style_title,artwork_type_title,credit_line," +
            "main_reference_number,is_on_view,gallery_title,inscriptions,description,short_description," +
            "provenance_text,exhibition_history,publication_history,image_id,thumbnail,is_public_domain"

        /**
         * The Art Institute tags works with Getty vocabulary terms, which repeat each other
         * ("oil paint (paint)", "paint", "painting techniques"). Keep the specific ones: drop the
         * qualifiers in brackets, duplicates, broader terms that a listed one ends with ("paint" in
         * "oil paint"), headings of the vocabulary ("... techniques") and the artwork's own type.
         */
        fun cleanTerms(terms: List<String>, type: String?): List<String> {
            val plain = terms.map { it.substringBefore(" (").trim().lowercase() }.filter { it.isNotEmpty() }.distinct()
            return plain.filter { term ->
                term != type && !term.endsWith(" techniques") &&
                    plain.none { other -> other != term && other.endsWith(" $term") }
            }
        }
    }
}

@Serializable
internal class AicResponse<T>(val data: T)

@Serializable
internal class AicArtwork(
    val id: Int,
    val title: String,
    @SerialName("artist_display") val artistDisplay: String? = null,
    @SerialName("artist_title") val artistTitle: String? = null,
    @SerialName("artist_id") val artistId: Int? = null,
    @SerialName("date_display") val dateDisplay: String? = null,
    @SerialName("place_of_origin") val placeOfOrigin: String? = null,
    @SerialName("medium_display") val medium: String? = null,
    val dimensions: String? = null,
    @SerialName("material_titles") val materials: List<String> = emptyList(),
    @SerialName("technique_titles") val techniques: List<String> = emptyList(),
    @SerialName("style_title") val style: String? = null,
    @SerialName("artwork_type_title") val type: String? = null,
    @SerialName("credit_line") val creditLine: String? = null,
    @SerialName("main_reference_number") val referenceNumber: String? = null,
    @SerialName("is_on_view") val onView: Boolean = false,
    @SerialName("gallery_title") val gallery: String? = null,
    val inscriptions: String? = null,
    val description: String? = null,
    @SerialName("short_description") val shortDescription: String? = null,
    @SerialName("provenance_text") val provenance: String? = null,
    @SerialName("exhibition_history") val exhibitions: String? = null,
    @SerialName("publication_history") val publications: String? = null,
    @SerialName("image_id") val imageId: String? = null,
    val thumbnail: AicThumbnail? = null,
)

@Serializable
internal class AicThumbnail(
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("alt_text") val altText: String? = null,
)

@Serializable
internal class AicAgent(val id: Int, val title: String, val description: String? = null)
