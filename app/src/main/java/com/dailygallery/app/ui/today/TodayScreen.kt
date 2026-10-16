package com.dailygallery.app.ui.today

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dailygallery.app.AppGraph
import com.dailygallery.app.domain.ArtImage
import com.dailygallery.app.domain.Day
import com.dailygallery.app.domain.PoolEntry
import com.dailygallery.app.ui.artwork.ArtworkPage
import com.dailygallery.app.ui.artwork.ArtworkViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Today's artwork. Coming back to the app after midnight shows the new day's. */
@Composable
fun TodayScreen(graph: AppGraph, onOpenImage: (image: ArtImage, caption: String) -> Unit, modifier: Modifier = Modifier) {
    var today by remember { mutableStateOf(graph.repository.today()) }
    LifecycleResumeEffect(Unit) {
        today = graph.repository.today()
        onPauseOrDispose { }
    }
    EntryPage(graph, today.entry, today.overline(today.date), onOpenImage, modifier)
}

/** An earlier day's artwork, or a saved one ([date] null), opened from the gallery. */
@Composable
fun DayScreen(
    graph: AppGraph,
    entry: PoolEntry,
    date: LocalDate?,
    onOpenImage: (image: ArtImage, caption: String) -> Unit,
    onBack: () -> Unit,
) {
    val overline = date?.let { Day(it, entry).overline(graph.repository.today().date) } ?: "Saved"
    EntryPage(graph, entry, overline, onOpenImage, navigationIcon = {
        IconButton(
            onClick = onBack,
            colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
    })
}

/** The page for any artwork in the schedule, loaded once per artwork, with its save button. */
@Composable
fun EntryPage(
    graph: AppGraph,
    entry: PoolEntry,
    overline: String,
    onOpenImage: (image: ArtImage, caption: String) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
) {
    val model: ArtworkViewModel = viewModel(key = entry.key) { ArtworkViewModel(entry, graph.repository) }
    val state by model.state.collectAsStateWithLifecycle()
    val saved by graph.saved.entries.collectAsStateWithLifecycle()
    val isSaved = saved.any { it.key == entry.key }
    ArtworkPage(entry, state, overline, model::retry, onOpenImage, modifier, navigationIcon) {
        IconToggleButton(
            checked = isSaved,
            onCheckedChange = { graph.saved.toggle(entry) },
            modifier = Modifier.semantics { stateDescription = if (isSaved) "Saved" else "Not saved" },
        ) {
            Icon(
                if (isSaved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = "Save",
                tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

fun dayName(date: LocalDate): String = date.format(dayFormat)

/** "Today · Friday 16 October", "Yesterday · …", or the date further back. */
fun Day.overline(today: LocalDate): String = when (date) {
    today -> "Today · ${dayName(date)}"
    today.minusDays(1) -> "Yesterday · ${dayName(date)}"
    else -> dayName(date)
}
