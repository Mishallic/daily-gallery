package com.dailygallery.app.ui.today

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    EntryPage(graph, today.entry, "Today · ${dayName(today.date)}", onOpenImage, modifier)
}

/** The page for any artwork in the schedule, loaded once per artwork. */
@Composable
fun EntryPage(
    graph: AppGraph,
    entry: PoolEntry,
    overline: String,
    onOpenImage: (image: ArtImage, caption: String) -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val model: ArtworkViewModel = viewModel(key = entry.key) { ArtworkViewModel(entry, graph.repository) }
    val state by model.state.collectAsStateWithLifecycle()
    ArtworkPage(entry, state, overline, model::retry, onOpenImage, modifier, navigationIcon, actions)
}

private val dayFormat = DateTimeFormatter.ofPattern("EEEE d MMMM")

fun dayName(date: LocalDate): String = date.format(dayFormat)

fun Day.overline(today: LocalDate): String = when (date) {
    today -> "Today · ${dayName(date)}"
    today.minusDays(1) -> "Yesterday · ${dayName(date)}"
    else -> dayName(date)
}
