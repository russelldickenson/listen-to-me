package com.listentome.app.ui.miniplayer

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.playback.PlaybackController
import com.listentome.app.playback.PlaybackUiState
import com.listentome.app.repository.PodcastRepository
import com.listentome.app.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MiniPlayerState(
    val episode: Episode,
    val feed: Feed?,
    /** True when the episode is loaded in the player; false when it's the queue head offered for resume. */
    val isLoaded: Boolean
)

@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val playbackController = PlaybackController.get(application)

    private val appSettings = AppSettings.get(application)

    val playback: StateFlow<PlaybackUiState> = playbackController.state

    init {
        // Lives for the whole session (unlike the player screen's view model), so progress
        // keeps being saved while playing in the background.
        viewModelScope.launch {
            playback.collect { state ->
                val id = state.currentEpisodeId ?: return@collect
                appSettings.setLastPlayedEpisodeId(id)
                if (state.positionMs > 0) {
                    val finished = state.durationMs > 0 && state.positionMs >= state.durationMs - 1000
                    repository.updatePlaybackProgress(id, state.positionMs, finished)
                }
            }
        }
    }

    private val loadedEpisode = playback
        .map { it.currentEpisodeId }
        .flatMapLatest { id -> if (id == null) flowOf<Episode?>(null) else repository.observeEpisode(id) }

    private val lastPlayed = appSettings.lastPlayedEpisodeId
        .flatMapLatest { id -> if (id == null) flowOf<Episode?>(null) else repository.observeEpisode(id) }
        .map { episode -> episode?.takeIf { !it.isFinished } }

    private val queueHead = repository.observeQueue().map { queue -> queue.firstOrNull { !it.isFinished } }

    /**
     * The loaded episode, or (e.g. after a cold start) the last-played unfinished episode,
     * falling back to the first unfinished queue episode.
     */
    val state: StateFlow<MiniPlayerState?> = combine(loadedEpisode, lastPlayed, queueHead) { loaded, last, head ->
        loaded?.let { it to true } ?: (last ?: head)?.let { it to false }
    }.flatMapLatest { pair ->
        if (pair == null) {
            flowOf<MiniPlayerState?>(null)
        } else {
            val (episode, isLoaded) = pair
            repository.observeFeed(episode.feedId).map { MiniPlayerState(episode, it, isLoaded) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun togglePlayPause() {
        val current = state.value ?: return
        viewModelScope.launch {
            if (current.isLoaded) {
                playbackController.togglePlayPause()
            } else {
                val episode = current.episode
                playbackController.playEpisode(
                    episodeId = episode.id,
                    title = episode.title,
                    artist = current.feed?.title ?: "",
                    artworkUri = episode.imageUrl ?: current.feed?.imageUrl,
                    uri = episode.localFilePath
                        ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                        ?: episode.audioUrl,
                    startPositionMs = episode.playbackPositionMs,
                    autoPlay = true
                )
            }
        }
    }
}
