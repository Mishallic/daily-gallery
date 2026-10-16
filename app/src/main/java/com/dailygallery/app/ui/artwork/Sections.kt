package com.dailygallery.app.ui.artwork

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.Event
import com.dailygallery.app.domain.Fact
import com.dailygallery.app.domain.Paragraph
import com.dailygallery.app.domain.Passage

/** Lines a text button up with the text above it. */
private val InlineButtonPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)

/** One part of the artwork page, with its short [label] for the section bar. */
internal class Section(val id: String, val label: String, val title: String, val content: @Composable (SectionContext) -> Unit)

internal class SectionContext(val artwork: Artwork, val refreshing: Boolean, val onRetry: () -> Unit)

private val materialFacts = setOf("Medium", "Materials", "Technique", "Support", "Dimensions", "Inscriptions")
private val museumFacts = setOf("On view", "Object number", "Credit line")

/** The sections this artwork has something for; Sources is always there. */
internal fun sectionsOf(artwork: Artwork): List<Section> = buildList {
    if (artwork.story.isNotEmpty()) {
        add(Section("story", "Story", "The story") { Passages(artwork.story) })
    }

    val materials = artwork.facts.filter { it.label in materialFacts }
    if (materials.isNotEmpty() || artwork.materials.isNotEmpty()) {
        add(
            Section("materials", "Materials", "Materials & technique") {
                FactsTable(materials)
                if (materials.isNotEmpty() && artwork.materials.isNotEmpty()) Spacer(Modifier.height(24.dp))
                Passages(artwork.materials)
            },
        )
    }

    if (artwork.owners.isNotEmpty() || artwork.exhibitions.isNotEmpty() || artwork.history.isNotEmpty()) {
        add(
            Section("history", "History", "Through history") {
                if (artwork.owners.isNotEmpty()) {
                    SubHeading("Owners", first = true)
                    Timeline(artwork.owners, shownAtFirst = 6, noun = "owners")
                }
                if (artwork.exhibitions.isNotEmpty()) {
                    SubHeading("Exhibitions", first = artwork.owners.isEmpty())
                    Timeline(artwork.exhibitions, shownAtFirst = 5, noun = "exhibitions")
                }
                Passages(artwork.history)
            },
        )
    }

    if (artwork.critics.isNotEmpty() || artwork.artistCritics.isNotEmpty() || artwork.publications.isNotEmpty()) {
        add(
            Section("critics", "Critics", "What critics wrote") {
                if (artwork.critics.isNotEmpty()) {
                    SubHeading("On this work", first = true)
                    Passages(artwork.critics)
                }
                if (artwork.artistCritics.isNotEmpty()) {
                    SubHeading("On ${artwork.artist}", first = artwork.critics.isEmpty())
                    Passages(artwork.artistCritics)
                }
                if (artwork.publications.isNotEmpty()) {
                    SubHeading("Written about in", first = artwork.critics.isEmpty() && artwork.artistCritics.isEmpty())
                    Timeline(artwork.publications, shownAtFirst = 5, noun = "publications")
                }
            },
        )
    }

    artwork.artistBio?.let { bio -> add(Section("artist", "Artist", "About the artist") { PassageView(bio) }) }

    add(Section("sources", "Sources", "Sources") { Sources(it) })
}

@Composable
internal fun SectionBlock(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 36.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        content()
    }
}

/** Heads a group within a section; groups after the [first] get room to breathe. */
@Composable
private fun SubHeading(text: String, first: Boolean) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(top = if (first) 4.dp else 28.dp, bottom = 12.dp),
    )
}

fun Paragraph.annotated(): AnnotatedString = buildAnnotatedString {
    append(text)
    italics.forEach { addStyle(SpanStyle(fontStyle = FontStyle.Italic), it.start, it.end) }
}

@Composable
private fun Passages(passages: List<Passage>) {
    passages.forEachIndexed { index, passage ->
        if (index > 0) Spacer(Modifier.height(28.dp))
        PassageView(passage)
    }
}

/** A passage, folded after about [foldAfter] characters, ending with where it comes from. */
@Composable
internal fun PassageView(passage: Passage, foldAfter: Int = 900) {
    val uriHandler = LocalUriHandler.current
    var expanded by rememberSaveable(passage.url, passage.title) { mutableStateOf(false) }
    val long = passage.paragraphs.sumOf { it.text.length } > foldAfter
    val budget = foldAfter * 2 / 3
    val shown = if (expanded || !long) passage.paragraphs else firstParagraphs(passage.paragraphs, budget)
    // Folded, a first paragraph longer than the whole budget is cut at a few lines.
    val clip = !expanded && long && shown.size == 1 && shown[0].text.length > budget

    Column {
        if (passage.title.isNotBlank()) {
            Text(passage.title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
        }
        shown.forEach { paragraph ->
            if (paragraph.subheading) {
                Text(paragraph.text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            } else {
                Text(
                    paragraph.annotated(),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (clip) 7 else Int.MAX_VALUE,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        if (long) {
            TextButton(onClick = { expanded = !expanded }, contentPadding = InlineButtonPadding) {
                Text(if (expanded) "Show less" else "Read more")
            }
        }
        val source = "Source: ${passage.source}" + if (passage.url != null) " ↗" else ""
        Text(
            source,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = if (passage.url != null) {
                Modifier.clickable(onClickLabel = "Open the source") { uriHandler.openUri(passage.url) }.padding(vertical = 4.dp)
            } else {
                Modifier.padding(vertical = 4.dp)
            },
        )
    }
}

/** Paragraphs up to about [chars] characters; always at least the first. */
private fun firstParagraphs(paragraphs: List<Paragraph>, chars: Int): List<Paragraph> {
    var total = 0
    return paragraphs.takeWhile { paragraph ->
        val keep = total == 0 || total + paragraph.text.length <= chars
        total += paragraph.text.length
        keep
    }
}

@Composable
private fun FactsTable(facts: List<Fact>) {
    Column {
        facts.forEachIndexed { index, fact ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.padding(vertical = 10.dp)) {
                Text(
                    fact.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(112.dp).padding(top = 2.dp),
                )
                Text(fact.value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Dated lines on a thread: owners, exhibitions, publications. Long lists open on request. */
@Composable
private fun Timeline(events: List<Event>, shownAtFirst: Int, noun: String) {
    var expanded by rememberSaveable(noun) { mutableStateOf(false) }
    val shown = if (expanded) events else events.take(shownAtFirst)
    val thread = MaterialTheme.colorScheme.outlineVariant
    val dot = MaterialTheme.colorScheme.primary

    Column(Modifier.padding(bottom = 12.dp)) {
        shown.forEachIndexed { index, event ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Text(
                    event.year?.toString().orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(40.dp).padding(top = 2.dp),
                )
                Canvas(Modifier.width(28.dp).fillMaxHeight()) {
                    val x = size.width / 2
                    val y = 11.dp.toPx()
                    val last = index == shown.lastIndex
                    drawLine(thread, Offset(x, if (index == 0) y else 0f), Offset(x, if (last) y else size.height), 1.5.dp.toPx())
                    drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, y))
                }
                Text(event.text.annotated(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 14.dp))
            }
        }
        if (events.size > shownAtFirst) {
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(start = 56.dp)) {
                Text(if (expanded) "Show fewer" else "Show all ${events.size} $noun")
            }
        }
    }
}

@Composable
private fun Sources(context: SectionContext) {
    val uriHandler = LocalUriHandler.current
    val artwork = context.artwork
    if (artwork.incomplete) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        ) {
            Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Wikipedia couldn’t be reached, so parts are missing.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (context.refreshing) {
                    CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = context.onRetry) { Text("Try again") }
                }
            }
        }
    }
    FactsTable(artwork.facts.filter { it.label in museumFacts })
    Spacer(Modifier.height(12.dp))
    artwork.credits.forEach { credit ->
        Text(
            "${credit.text} ↗",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "Open") { uriHandler.openUri(credit.url) }.padding(vertical = 8.dp),
        )
    }
}
