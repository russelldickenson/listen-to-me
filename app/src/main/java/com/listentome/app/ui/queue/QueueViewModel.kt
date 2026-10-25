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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val reorderHintDismissed: StateFlow<Boolean> = appSettings.reorderHintDismissed

    fun dismissReorderHint() {
        appSettings.setReorderHintDismissed(true)
    }

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

    val hasStaleFeeds: StateFlow<Boolean> = repository.observeHasStaleFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _refreshProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (completed, total) podcasts refreshed so far, or null when not refreshing. */
    val refreshProgress: StateFlow<Pair<Int, Int>?> = _refreshProgress.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    fun refreshAll() {
        if (_refreshProgress.value != null) return
        viewModelScope.launch {
            _refreshProgress.value = 0 to 1
            try {
                val failed = repository.refreshAllFeeds { completed, total ->
                    _refreshProgress.value = completed to total
                }
                if (failed.isNotEmpty()) {
                    _errorMessages.emit(
                        if (failed.size == 1) {
                            "Failed to refresh \"${failed[0].first}\": ${failed[0].second}"
                        } else {
                            "Failed to refresh ${failed.size} podcasts"
                        }
                    )
                }
            } finally {
                _refreshProgress.value = null
            }
        }
    }

    fun reorderQueue(orderedEpisodeIds: List<Long>) {
        viewModelScope.launch { repository.reorderQueue(orderedEpisodeIds) }
    }

    /** [episode] must be the snapshot taken before removal, so its old queue position is intact. */
    fun restoreToQueue(episode: Episode) {
        val position = episode.queuePosition ?: return
        viewModelScope.launch { repository.restoreQueuePosition(episode.id, position) }
    }

    fun download(episode: Episode) {
        if (repository.isWifiBlockingDownloads()) {
            _errorMessages.tryEmit("Wi-Fi only downloads is on. Connect to Wi-Fi or turn it off in Settings.")
            return
        }
        viewModelScope.launch { repository.downloadEpisode(episode) }
    }

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { repository.deleteDownload(episode) }
    }

    fun markAsPlayed(episode: Episode) {
        viewModelScope.launch { repository.markAsPlayed(episode.id) }
    }

    fun removeFromQueue(episode: Episode) {
        viewModelScope.launch { repository.removeFromQueue(episode.id) }
    }

    /**
     * Starts the queue as a playlist from [item] (so playback advances to the next queued episode),
     * or resumes it if it's already the current one, then opens the full-screen player.
     */
    fun openEpisode(item: QueueItem, onOpenPlayer: () -> Unit) {
        val state = playback.value
        if (state.currentEpisodeId != item.episode.id) {
            playFrom(item)
        } else if (!state.isPlaying) {
            viewModelScope.launch { playbackController.togglePlayPause() }
        }
        onOpenPlayer()
    }

    private fun playFrom(item: QueueItem) {
        val items = queue.value
        val startIndex = items.indexOfFirst { it.episode.id == item.episode.id }
        if (startIndex < 0) return
        viewModelScope.launch {
            val tracks = items.map { queued ->
                QueueTrack(
                    episodeId = queued.episode.id,
                    title = queued.episode.title,
                    artist = queued.feed?.title ?: "",
                    artworkUri = queued.episode.imageUrl ?: queued.feed?.imageUrl,
                    uri = queued.episode.localFilePath
                        ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                        ?: queued.episode.audioUrl
                )
            }
            playbackController.playQueue(tracks, startIndex, item.episode.playbackPositionMs)
        }
    }
}
