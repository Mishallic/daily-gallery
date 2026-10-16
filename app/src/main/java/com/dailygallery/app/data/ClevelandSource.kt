package com.dailygallery.app.data

import com.dailygallery.app.domain.ArtImage
import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.Credit
import com.dailygallery.app.domain.Event
import com.dailygallery.app.domain.Fact
import com.dailygallery.app.domain.Passage
import com.dailygallery.app.domain.Source
import com.dailygallery.app.domain.chronological
import com.dailygallery.app.domain.firstYear
import com.dailygallery.app.domain.htmlLine
import com.dailygallery.app.domain.htmlParagraphs
import com.dailygallery.app.domain.ownerYear
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The Cleveland Museum of Art's Open Access API: no key, records and images CC0. */
class ClevelandSource(private val http: HttpGet, private val api: String = "https://openaccess-api.clevelandart.org/api") {

    suspend fun artwork(id: String): Artwork =
        toArtwork(sourceJson.decodeFromString<CmaResponse>(http.get("$api/artworks/$id")).data)

    private fun toArtwork(work: CmaArtwork): Artwork {
        val museum = Source.CMA.museum
        val pageUrl = work.url ?: "https://www.clevelandart.org/art/${work.accessionNumber}"
        val creator = work.creators.firstOrNull { it.role == "artist" } ?: work.creators.firstOrNull()
        // "Claude Monet (French, 1840–1926)"; unattributed works are known by their culture.
        val name = creator?.description?.substringBefore(" (") ?: work.culture.firstOrNull() ?: "Unknown maker"
        val lifeAndPlace = creator?.description?.let { Regex("""\((.+)\)$""").find(it)?.groupValues?.get(1) }
        val details = listOfNotNull(creator?.qualifier?.replaceFirstChar { it.uppercase() }, lifeAndPlace)
            .joinToString(" · ").ifEmpty { null }

        val story = buildList {
            work.description?.let { htmlParagraphs(it) }?.takeIf { it.isNotEmpty() }?.let { add(Passage("", museum, it, pageUrl)) }
            work.didYouKnow?.let { htmlParagraphs(it) }?.takeIf { it.isNotEmpty() }?.let { add(Passage("Did you know?", museum, it, pageUrl)) }
        }
        val supports = work.supportMaterials.mapNotNull { support ->
            // Paper colours carry the museum's shade numbers: "cream(2) wove paper".
            support.description?.replace(Regex("""\(\d+\)"""), "")?.trim()?.takeIf { it.isNotEmpty() }
        }
        val inscriptions = work.inscriptions.mapNotNull { inscription ->
            val text = inscription.inscription?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            inscription.translation?.trim()?.takeIf { it.isNotEmpty() }?.let { "$text ($it)" } ?: text
        }
        val facts = listOfNotNull(
            work.originalTitle?.takeIf { it != work.title }?.let { Fact("Original title", it) },
            work.technique?.let { Fact("Medium", it.replaceFirstChar { c -> c.uppercase() }) },
            supports.takeIf { it.isNotEmpty() }?.let { Fact("Support", it.joinToString("; ")) },
            work.measurements?.let { Fact("Dimensions", it) },
            inscriptions.takeIf { it.isNotEmpty() }?.let { Fact("Inscriptions", it.joinToString("; ")) },
            work.creditLine?.let { Fact("Credit line", it.replace(Regex("""\s+"""), " ")) },
            work.accessionNumber?.let { Fact("Object number", it) },
            work.location?.let { Fact("On view", "${gallery(it)}, $museum") },
        )

        return Artwork(
            source = Source.CMA,
            id = work.id.toString(),
            title = work.title,
            artist = name,
            artistDetails = details,
            date = work.creationDate.orEmpty(),
            image = work.images?.web?.url?.let { url ->
                ArtImage(
                    url = url,
                    largeUrl = work.images.print?.url ?: url,
                    width = work.images.web.width?.toIntOrNull(),
                    height = work.images.web.height?.toIntOrNull(),
                )
            },
            pageUrl = pageUrl,
            tags = listOfNotNull(work.type, work.culture.firstOrNull()).distinct(),
            story = story,
            facts = facts,
            materials = work.conservation?.let { htmlParagraphs(it) }?.takeIf { it.isNotEmpty() }
                ?.let { listOf(Passage("Conservation", museum, it, pageUrl)) }.orEmpty(),
            owners = work.provenance.mapNotNull { owner ->
                val text = htmlLine(owner.description ?: return@mapNotNull null) ?: return@mapNotNull null
                Event(owner.date?.let { firstYear(it) } ?: ownerYear(text.text), text)
            },
            exhibitions = (work.exhibitions?.legacy.orEmpty() + work.exhibitions?.current.orEmpty()).mapNotNull { show ->
                val text = htmlLine(show.description ?: show.title ?: return@mapNotNull null) ?: return@mapNotNull null
                Event(show.openingDate?.take(4)?.toIntOrNull() ?: firstYear(text.text), text)
            }.chronological(),
            publications = work.citations.mapNotNull { citation ->
                val text = htmlLine(citation.citation ?: return@mapNotNull null) ?: return@mapNotNull null
                Event(firstYear(text.text), text)
            }.chronological(),
            // Some "biographies" are only the artist's dates ("c. 400/390-330/325 BC").
            artistBio = creator?.biography?.takeIf { it.length >= 80 }?.let { Passage(name, museum, htmlParagraphs(it), pageUrl) },
            credits = listOf(Credit("Image and record: $museum, CC0", pageUrl)),
        )
    }

    /** "222 Impressionism & Post-Impressionism" is gallery 222. */
    private fun gallery(location: String): String =
        Regex("""^(\d+)\s+(.+)$""").find(location)?.let { "Gallery ${it.groupValues[1]} (${it.groupValues[2]})" } ?: location
}

@Serializable
internal class CmaResponse(val data: CmaArtwork)

@Serializable
internal class CmaArtwork(
    val id: Int,
    val title: String,
    @SerialName("title_in_original_language") val originalTitle: String? = null,
    @SerialName("accession_number") val accessionNumber: String? = null,
    @SerialName("creation_date") val creationDate: String? = null,
    val creators: List<CmaCreator> = emptyList(),
    val culture: List<String> = emptyList(),
    val technique: String? = null,
    val type: String? = null,
    val measurements: String? = null,
    val description: String? = null,
    @SerialName("did_you_know") val didYouKnow: String? = null,
    @SerialName("conservation_statement") val conservation: String? = null,
    @SerialName("support_materials") val supportMaterials: List<CmaSupport> = emptyList(),
    val inscriptions: List<CmaInscription> = emptyList(),
    val provenance: List<CmaOwner> = emptyList(),
    val citations: List<CmaCitation> = emptyList(),
    val exhibitions: CmaExhibitions? = null,
    @SerialName("creditline") val creditLine: String? = null,
    @SerialName("current_location") val location: String? = null,
    val url: String? = null,
    val images: CmaImages? = null,
)

@Serializable
internal class CmaCreator(
    val description: String? = null,
    val role: String? = null,
    val qualifier: String? = null,
    val biography: String? = null,
)

@Serializable
internal class CmaSupport(val description: String? = null)

@Serializable
internal class CmaInscription(
    val inscription: String? = null,
    @SerialName("inscription_translation") val translation: String? = null,
)

@Serializable
internal class CmaOwner(val description: String? = null, val date: String? = null)

@Serializable
internal class CmaCitation(val citation: String? = null)

@Serializable
internal class CmaExhibitions(val current: List<CmaExhibition> = emptyList(), val legacy: List<CmaExhibition> = emptyList())

@Serializable
internal class CmaExhibition(
    val title: String? = null,
    val description: String? = null,
    @SerialName("opening_date") val openingDate: String? = null,
)

@Serializable
internal class CmaImages(val web: CmaImage? = null, val print: CmaImage? = null)

@Serializable
internal class CmaImage(val url: String? = null, val width: String? = null, val height: String? = null)
