package com.dailygallery.app.domain

import kotlinx.serialization.Serializable

/**
 * Everything the app shows about one artwork, put together from its museum's record and, where they
 * exist, the Wikipedia articles on the work and its artist. Nothing is written by the app: every
 * passage says where it comes from.
 */
@Serializable
data class Artwork(
    val source: Source,
    val id: String,
    val title: String,
    val artist: String,
    /** "French, 1859–1891", or the culture for unattributed works. */
    val artistDetails: String? = null,
    val date: String,
    val image: ArtImage? = null,
    /** The artwork's page on the museum's website. */
    val pageUrl: String,
    /** Short labels under the title: type, place, style. */
    val tags: List<String> = emptyList(),
    val story: List<Passage> = emptyList(),
    /** Medium, materials, techniques, dimensions, inscriptions, credit line, ... */
    val facts: List<Fact> = emptyList(),
    val materials: List<Passage> = emptyList(),
    /** Who owned it, oldest first. */
    val owners: List<Event> = emptyList(),
    val exhibitions: List<Event> = emptyList(),
    val history: List<Passage> = emptyList(),
    /** What critics and scholars wrote about this work. */
    val critics: List<Passage> = emptyList(),
    /** What critics wrote about the artist, from the artist's Wikipedia article. */
    val artistCritics: List<Passage> = emptyList(),
    /** Books and articles that discuss the work, oldest first. */
    val publications: List<Event> = emptyList(),
    val artistBio: Passage? = null,
    val credits: List<Credit> = emptyList(),
    /** An optional source (Wikipedia) couldn't be read; the next visit tries it again. */
    val incomplete: Boolean = false,
)

@Serializable
data class ArtImage(
    val url: String,
    /** A sharper copy for the full-screen viewer. */
    val largeUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val altText: String? = null,
)

/** A titled piece of writing, the name of its [source], and a link to it. */
@Serializable
data class Passage(val title: String, val source: String, val paragraphs: List<Paragraph>, val url: String? = null)

/** Plain text and the parts of it in italics (museums set the titles of works in italics). */
@Serializable
data class Paragraph(val text: String, val italics: List<Span> = emptyList(), val subheading: Boolean = false)

@Serializable
data class Span(val start: Int, val end: Int)

/** A line in the artwork's history (an owner, an exhibition, a publication) and its year, if it has one. */
@Serializable
data class Event(val year: Int?, val text: Paragraph)

@Serializable
data class Fact(val label: String, val value: String)

@Serializable
data class Credit(val text: String, val url: String)
