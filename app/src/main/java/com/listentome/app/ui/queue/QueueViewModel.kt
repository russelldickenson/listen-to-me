package com.listentome.app.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.playback.PlaybackController
import com.listentome.app.playback.PlaybackUiState
import com.listentome.app.playback.QueueTrack
import com.listentome.app.repository.PodcastRepository
import com.listentome.app.settings.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueItem(val episode: Episode, val feed: Feed?)

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val playbackController = PlaybackController.get(application)
    private val appSettings = AppSettings.get(application)

    val playback: StateFlow<PlaybackUiState> = playbackController.state
    val downloadProgress: StateFlow<Map<Long, Float>> = repository.downloadProgress

    val queue: StateFlow<List<QueueItem>> = combine(
        repository.observeQueue(),
        repository.observeFeeds(),
        appSettings.hidePlayedEpisodes
    ) { episodes, feeds, hidePlayed ->
        val feedsById = feeds.associateBy { it.id }
        episodes
            .filter { !hidePlayed || !it.isFinished }
            .map { QueueItem(it, feedsById[it.feedId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun reorderQueue(orderedEpisodeIds: List<Long>) {
        viewModelScope.launch { repository.reorderQueue(orderedEpisodeIds) }
    }

    fun removeFromQueue(episode: Episode) {
        viewModelScope.launch { repository.removeFromQueue(episode.id) }
    }

    /**
     * Loads the given item (without starting playback) unless it's already the current one,
     * then opens the full-screen player. Play/pause only happens from the player screen.
     */
    fun openEpisode(item: QueueItem, onOpenPlayer: () -> Unit) {
        if (playback.value.currentEpisodeId != item.episode.id) {
            play(item)
        }
        onOpenPlayer()
    }

    /** Toggles play/pause if the current track is already part of the queue, otherwise starts playing the queue from the top. */
    fun toggleQueuePlayback() {
        val items = queue.value
        if (items.isEmpty()) return
        if (playback.value.isQueuePlaylist && items.any { it.episode.id == playback.value.currentEpisodeId }) {
            viewModelScope.launch { playbackController.togglePlayPause() }
        } else {
            viewModelScope.launch {
                val tracks = items.map { item ->
                    QueueTrack(
                        episodeId = item.episode.id,
                        title = item.episode.title,
                        artist = item.feed?.title ?: "",
                        artworkUri = item.episode.imageUrl ?: item.feed?.imageUrl,
                        uri = item.episode.localFilePath
                            ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                            ?: item.episode.audioUrl
                    )
                }
                playbackController.playQueue(tracks, startIndex = 0, startPositionMs = items[0].episode.playbackPositionMs)
            }
        }
    }

    private fun play(item: QueueItem) {
        viewModelScope.launch {
            val source = item.episode.localFilePath
                ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                ?: item.episode.audioUrl
            playbackController.playEpisode(
                episodeId = item.episode.id,
                title = item.episode.title,
                artist = item.feed?.title ?: "",
                artworkUri = item.episode.imageUrl ?: item.feed?.imageUrl,
                uri = source,
                startPositionMs = item.episode.playbackPositionMs,
                autoPlay = false
            )
        }
    }
}
