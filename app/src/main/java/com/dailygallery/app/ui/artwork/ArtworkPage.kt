package com.dailygallery.app.ui.artwork

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.dailygallery.app.domain.ArtImage
import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.PoolEntry
import kotlinx.coroutines.launch

/** Items above the sections in the list: the picture, its label, the actions, the section bar. */
private const val FIRST_SECTION = 4

/**
 * One artwork's page: the picture hung on a wall, its label, then the story, materials, history,
 * critics and artist. The label comes from the schedule, so it shows at once, even offline; the
 * rest follows when the museum's record is read.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtworkPage(
    entry: PoolEntry,
    state: ArtworkState,
    overline: String,
    onRetry: () -> Unit,
    onOpenImage: (image: ArtImage, caption: String) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val ready = state as? ArtworkState.Ready
    val artwork = ready?.artwork
    val sections = remember(artwork) { artwork?.let { sectionsOf(it) }.orEmpty() }
    val barHeight = with(LocalDensity.current) { 56.dp.roundToPx() }
    // The section just below the bar: after a jump, the one before it still peeks out under the bar.
    val current by remember(sections) {
        derivedStateOf {
            val below = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index >= FIRST_SECTION && it.offset + it.size > barHeight + 1 }
            ((below?.index ?: FIRST_SECTION) - FIRST_SECTION).coerceIn(0, maxOf(sections.size - 1, 0))
        }
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize().semantics { contentDescription = "Artwork page" }) {
        item(key = "picture") { Picture(entry, artwork, onOpenImage, navigationIcon) }
        item(key = "label") { ArtworkLabel(entry, artwork, overline) }
        item(key = "actions") { ActionRow(entry, artwork, actions) }
        when (state) {
            ArtworkState.Loading -> item(key = "loading") { Loading() }
            is ArtworkState.Failed -> item(key = "failed") { Failed(state, entry, onRetry) }
            is ArtworkState.Ready -> {
                stickyHeader(key = "sections") {
                    SectionBar(sections, current) { index ->
                        scope.launch { listState.animateScrollToItem(FIRST_SECTION + index, -barHeight) }
                    }
                }
                sections.forEach { section ->
                    item(key = section.id) {
                        SectionBlock(section.title) { section.content(SectionContext(state.artwork, state.refreshing, onRetry)) }
                    }
                }
            }
        }
        item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(48.dp)) }
    }
}

/** The artwork on a wall: the full image once the record is read, the schedule's thumbnail until then. */
@Composable
private fun Picture(
    entry: PoolEntry,
    artwork: Artwork?,
    onOpenImage: (image: ArtImage, caption: String) -> Unit,
    navigationIcon: (@Composable () -> Unit)?,
) {
    val image = artwork?.image
    val ratio = image?.let { if ((it.width ?: 0) > 0 && (it.height ?: 0) > 0) it.width!!.toFloat() / it.height!! else null }
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)) {
        Box(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            val frame = if (ratio != null) {
                Modifier.heightIn(max = 460.dp).aspectRatio(ratio.coerceIn(0.4f, 2.6f)).shadow(14.dp)
            } else {
                Modifier.fillMaxWidth().height(320.dp)
            }
            AsyncImage(
                model = image?.url ?: entry.thumbnail,
                contentDescription = image?.altText ?: "${entry.title}, by ${entry.artist}",
                contentScale = ContentScale.Fit,
                modifier = frame.clickable(enabled = image != null, onClickLabel = "View full screen") {
                    image?.let { onOpenImage(it, "${artwork.title}, ${artwork.artist}") }
                },
            )
        }
        if (navigationIcon != null) {
            Box(Modifier.statusBarsPadding().padding(8.dp)) { navigationIcon() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArtworkLabel(entry: PoolEntry, artwork: Artwork?, overline: String) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 28.dp)) {
        Text(overline.uppercase(), style = MaterialTheme.typography.labelMedium, color = colors.primary)
        Spacer(Modifier.height(10.dp))
        Text(artwork?.title ?: entry.title, style = MaterialTheme.typography.headlineMedium)
        artwork?.facts?.firstOrNull { it.label == "Original title" }?.let {
            Text(it.value, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        Text(artwork?.artist ?: entry.artist, style = MaterialTheme.typography.titleMedium)
        artwork?.artistDetails?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        Text(artwork?.date ?: entry.date, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        val tags = artwork?.tags.orEmpty()
        if (tags.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    Surface(shape = RoundedCornerShape(50), color = colors.secondaryContainer) {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionRow(entry: PoolEntry, artwork: Artwork?, actions: @Composable RowScope.() -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        actions()
        IconButton(onClick = {
            val text = buildString {
                append("${artwork?.title ?: entry.title}, ${artwork?.artist ?: entry.artist}")
                if (entry.date.isNotEmpty()) append(", ${artwork?.date ?: entry.date}")
                artwork?.pageUrl?.let { append("\n$it") }
            }
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null))
        }) {
            Icon(Icons.Filled.Share, contentDescription = "Share")
        }
        if (artwork != null) {
            TextButton(onClick = { uriHandler.openUri(artwork.pageUrl) }) { Text("See it at the museum") }
        }
    }
}

@Composable
private fun SectionBar(sections: List<Section>, current: Int, onSelect: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEachIndexed { index, section ->
                FilterChip(selected = index == current, onClick = { onSelect(index) }, label = { Text(section.label) })
            }
        }
    }
}

@Composable
private fun Loading() {
    Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.size(12.dp))
        Text("Reading the museum’s record…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Failed(state: ArtworkState.Failed, entry: PoolEntry, onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(if (state.offline) "You’re offline" else "The museum didn’t answer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (state.offline) {
                    "The story, materials and history need a connection the first time. Once read, they stay on your phone."
                } else {
                    "The ${entry.source.museum} couldn’t be reached. Try again in a moment."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
