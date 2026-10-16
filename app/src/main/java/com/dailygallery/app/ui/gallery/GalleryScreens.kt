package com.dailygallery.app.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dailygallery.app.AppGraph
import com.dailygallery.app.domain.Day
import com.dailygallery.app.domain.PoolEntry
import com.dailygallery.app.ui.theme.overline
import java.time.format.DateTimeFormatter

private val shortDay = DateTimeFormatter.ofPattern("EEE d MMM")

/** Every day's artwork before today, newest first: as far back as the schedule goes without repeating. */
@Composable
fun GalleryScreen(graph: AppGraph, onOpen: (Day) -> Unit) {
    var today by remember { mutableStateOf(graph.repository.today().date) }
    LifecycleResumeEffect(Unit) {
        today = graph.repository.today().date
        onPauseOrDispose { }
    }
    val days = remember(today) { graph.repository.recentDays(graph.pool.entries.size).drop(1) }
    ArtworkGrid(
        title = "Gallery",
        subtitle = "An artwork for every day, newest first.",
        entries = days.map { it.entry },
        label = { index -> if (days[index].date == today.minusDays(1)) "Yesterday" else days[index].date.format(shortDay) },
        onOpen = { index -> onOpen(days[index]) },
    )
}

/** The artworks the user saved, newest first. */
@Composable
fun SavedScreen(graph: AppGraph, onOpen: (PoolEntry) -> Unit) {
    val saved by graph.saved.entries.collectAsStateWithLifecycle()
    ArtworkGrid(
        title = "Saved",
        subtitle = if (saved.isEmpty()) {
            "Nothing saved yet. Tap the heart on an artwork to keep it here, ready to open offline."
        } else {
            "Kept on this phone, ready to open offline."
        },
        entries = saved,
        label = { null }, // no day to show: the artwork's own date goes after the artist
        onOpen = { index -> onOpen(saved[index]) },
    )
}

@Composable
private fun ArtworkGrid(
    title: String,
    subtitle: String,
    entries: List<PoolEntry>,
    label: (Int) -> String?,
    onOpen: (Int) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 24.dp, bottom = 16.dp)) {
                Text(title, style = MaterialTheme.typography.displaySmall)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
            Tile(entry, label(index)) { onOpen(index) }
        }
    }
}

@Composable
private fun Tile(entry: PoolEntry, label: String?, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) {}.padding(12.dp),
    ) {
        AsyncImage(
            model = entry.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Spacer(Modifier.height(10.dp))
        if (label != null) {
            Text(label.uppercase(), style = MaterialTheme.typography.overline, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Spacer(Modifier.height(2.dp))
        }
        Text(entry.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            if (label == null && entry.date.isNotEmpty()) "${entry.artist}, ${entry.date}" else entry.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
