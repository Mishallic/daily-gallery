package com.dailygallery.app.domain

// Turning the museums' and Wikipedia's text into paragraphs, and finding the year in a line of history.

private val tag = Regex("<(/?)([a-zA-Z][a-zA-Z0-9]*)[^>]*>")
private val blankLine = Regex("""\n\s*\n""")
private val entity = Regex("&(#[xX][0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private val year = Regex("""\b(1[2-9]\d\d|20\d\d)\b""")
private val namedEntities = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "ndash" to "–", "mdash" to "—", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”",
    "hellip" to "…", "eacute" to "é", "egrave" to "è", "agrave" to "à", "uuml" to "ü", "ouml" to "ö",
    "auml" to "ä", "ccedil" to "ç", "times" to "×", "deg" to "°",
)
private val blockTags = setOf("p", "div", "br", "li", "ul", "ol", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6", "tr")
private val italicTags = setOf("em", "i", "cite")

fun decodeEntities(text: String): String = entity.replace(text) { match ->
    val name = match.groupValues[1]
    when {
        name.startsWith("#x") || name.startsWith("#X") -> name.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        name.startsWith("#") -> name.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> namedEntities[name]
    } ?: match.value
}

/**
 * Museum text as paragraphs: HTML (<p>, <br>, <em>, links) or plain text with blank lines between
 * paragraphs. Tags are dropped, entities decoded and runs of whitespace closed up; italics are kept.
 */
fun htmlParagraphs(html: String): List<Paragraph> {
    val paragraphs = mutableListOf<Paragraph>()
    val text = StringBuilder()
    val italics = mutableListOf<Span>()
    var italicDepth = 0
    var italicStart = 0

    fun flush() {
        while (text.isNotEmpty() && text.last() == ' ') text.setLength(text.length - 1)
        if (italicDepth > 0) italics += Span(italicStart, text.length)
        if (text.isNotEmpty()) {
            val spans = italics.map { Span(it.start, minOf(it.end, text.length)) }.filter { it.end > it.start }
            paragraphs += Paragraph(text.toString(), spans)
        }
        text.clear()
        italics.clear()
        italicStart = 0 // an italic run that spans paragraphs carries on from the next one's start
    }

    fun append(raw: String) {
        raw.split(blankLine).forEachIndexed { index, part ->
            if (index > 0) flush()
            for (ch in decodeEntities(part)) {
                if (ch.isWhitespace()) {
                    if (text.isNotEmpty() && text.last() != ' ') text.append(' ')
                } else {
                    text.append(ch)
                }
            }
        }
    }

    var position = 0
    for (match in tag.findAll(html)) {
        append(html.substring(position, match.range.first))
        val closing = match.groupValues[1] == "/"
        val name = match.groupValues[2].lowercase()
        when {
            name in blockTags -> flush()
            name in italicTags && !closing -> if (italicDepth++ == 0) italicStart = text.length
            name in italicTags && italicDepth > 0 -> if (--italicDepth == 0) italics += Span(italicStart, text.length)
        }
        position = match.range.last + 1
    }
    append(html.substring(position))
    flush()
    return paragraphs
}

/** One line of museum text (an exhibition, a citation) as a single paragraph. */
fun htmlLine(html: String): Paragraph? {
    val paragraphs = htmlParagraphs(html)
    if (paragraphs.size <= 1) return paragraphs.firstOrNull()
    val joined = StringBuilder()
    val spans = mutableListOf<Span>()
    for (paragraph in paragraphs) {
        if (joined.isNotEmpty()) joined.append(' ')
        val offset = joined.length
        joined.append(paragraph.text)
        paragraph.italics.mapTo(spans) { Span(it.start + offset, it.end + offset) }
    }
    return Paragraph(joined.toString(), spans)
}

/** Text separated by blank lines, the way the Art Institute lists exhibitions and publications. */
fun blocks(text: String): List<String> = text.split(blankLine).map { it.trim() }.filter { it.isNotEmpty() }

/** Splits at [separator] except inside brackets: "A (1; 2), Paris; B" is two owners, not three. */
fun splitOutsideBrackets(text: String, separator: Char = ';'): List<String> {
    val parts = mutableListOf<String>()
    var depth = 0
    var from = 0
    text.forEachIndexed { index, ch ->
        when (ch) {
            '(', '[' -> depth++
            ')', ']' -> if (depth > 0) depth--
            separator -> if (depth == 0) {
                parts += text.substring(from, index)
                from = index + 1
            }
        }
    }
    parts += text.substring(from)
    return parts.map { it.trim() }.filter { it.isNotEmpty() }
}

/** The first year (1200-2099) in a line of history; house numbers and catalogue numbers are too short. */
fun firstYear(text: String): Int? = year.find(text)?.value?.toInt()

/**
 * The year an owner acquired a work: the last year outside brackets ("..., the artist's mother
 * (died 1899), Paris, 1891" is 1891), or failing that the last year at all.
 */
fun ownerYear(text: String): Int? {
    val outside = StringBuilder()
    var depth = 0
    for (ch in text) {
        when (ch) {
            '(', '[' -> depth++
            ')', ']' -> if (depth > 0) depth--
            else -> outside.append(if (depth == 0) ch else ' ')
        }
    }
    val last = year.findAll(outside).lastOrNull() ?: year.findAll(text).lastOrNull()
    return last?.value?.toInt()
}

/** Events oldest first; undated ones keep their place at the end. */
fun List<Event>.chronological(): List<Event> = sortedBy { it.year ?: Int.MAX_VALUE }
