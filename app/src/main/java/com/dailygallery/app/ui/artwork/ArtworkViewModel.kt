package com.dailygallery.app.ui.artwork

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dailygallery.app.data.ArtworkRepository
import com.dailygallery.app.data.FetchException
import com.dailygallery.app.domain.Artwork
import com.dailygallery.app.domain.PoolEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface ArtworkState {
    data object Loading : ArtworkState

    data class Ready(val artwork: Artwork, val refreshing: Boolean = false) : ArtworkState

    /** Nothing to show: [offline] when no connection, otherwise the museum didn't answer. */
    data class Failed(val offline: Boolean) : ArtworkState
}

/** Loads one artwork. Survives rotation, so the artwork isn't read twice. */
class ArtworkViewModel(private val entry: PoolEntry, private val repository: ArtworkRepository) : ViewModel() {
    private val _state = MutableStateFlow<ArtworkState>(ArtworkState.Loading)
    val state: StateFlow<ArtworkState> = _state

    init {
        load(refresh = false)
    }

    /** Reads the sources again: after a failure, or to fill in what Wikipedia couldn't give. */
    fun retry() = load(refresh = true)

    private fun load(refresh: Boolean) {
        viewModelScope.launch {
            val shown = (_state.value as? ArtworkState.Ready)?.artwork
            _state.value = if (shown != null) ArtworkState.Ready(shown, refreshing = true) else ArtworkState.Loading
            _state.value = try {
                ArtworkState.Ready(repository.artwork(entry, refresh))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Keep what is on screen; otherwise say why there is nothing.
                shown?.let { ArtworkState.Ready(it) } ?: ArtworkState.Failed(offline = failure is FetchException.Offline)
            }
        }
    }
}
