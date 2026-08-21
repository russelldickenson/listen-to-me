package com.listentome.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.playback.PlaybackController
import com.listentome.app.playback.PlaybackUiState
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val playbackController = PlaybackController.get(application)

    val playback: StateFlow<PlaybackUiState> = playbackController.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackUiState())

    val currentEpisode: StateFlow<Episode?> = playback
        .flatMapLatest { state ->
            val id = state.currentEpisodeId
            if (id == null) {
                flowOf<Episode?>(null)
            } else {
                repository.observeEpisode(id)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentFeed: StateFlow<Feed?> = currentEpisode
        .flatMapLatest { episode ->
            if (episode == null) flowOf<Feed?>(null) else repository.observeFeed(episode.feedId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            playback.collect { state ->
                val id = state.currentEpisodeId ?: return@collect
                if (state.positionMs > 0) {
                    val finished = state.durationMs > 0 && state.positionMs >= state.durationMs - 1000
                    repository.updatePlaybackProgress(id, state.positionMs, finished)
                }
            }
        }
    }

    fun togglePlayPause() {
        viewModelScope.launch { playbackController.togglePlayPause() }
    }

    fun seekTo(positionMs: Long) {
        viewModelScope.launch { playbackController.seekTo(positionMs) }
    }

    fun skip(deltaMs: Long) {
        viewModelScope.launch { playbackController.seekBy(deltaMs) }
    }
}
