package com.listentome.app.ui.queue

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.playback.PlaybackController
import com.listentome.app.playback.PlaybackUiState
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class QueueItem(val episode: Episode, val feed: Feed?)

class QueueViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val playbackController = PlaybackController.get(application)

    val playback: StateFlow<PlaybackUiState> = playbackController.state

    val queue: StateFlow<List<QueueItem>> = combine(
        repository.observeQueue(),
        repository.observeFeeds()
    ) { episodes, feeds ->
        val feedsById = feeds.associateBy { it.id }
        episodes.map { QueueItem(it, feedsById[it.feedId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun moveUp(episode: Episode) {
        val list = queue.value
        val index = list.indexOfFirst { it.episode.id == episode.id }
        if (index > 0) {
            viewModelScope.launch { repository.swapQueuePositions(episode.id, list[index - 1].episode.id) }
        }
    }

    fun moveDown(episode: Episode) {
        val list = queue.value
        val index = list.indexOfFirst { it.episode.id == episode.id }
        if (index in 0 until list.lastIndex) {
            viewModelScope.launch { repository.swapQueuePositions(episode.id, list[index + 1].episode.id) }
        }
    }

    fun removeFromQueue(episode: Episode) {
        viewModelScope.launch { repository.removeFromQueue(episode.id) }
    }

    /** Plays the given item, or toggles play/pause in place if it's already the current one. */
    fun playOrToggle(item: QueueItem, onStartedNewEpisode: () -> Unit) {
        if (playback.value.currentEpisodeId == item.episode.id) {
            viewModelScope.launch { playbackController.togglePlayPause() }
        } else {
            play(item)
            onStartedNewEpisode()
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
                startPositionMs = item.episode.playbackPositionMs
            )
        }
    }
}
