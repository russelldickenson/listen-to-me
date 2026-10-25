package com.listentome.app.ui.episodelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.DownloadState
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.playback.PlaybackController
import com.listentome.app.playback.PlaybackUiState
import com.listentome.app.repository.PodcastRepository
import com.listentome.app.settings.AppSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 10
private const val WIFI_ONLY_MESSAGE = "Wi-Fi only downloads is on. Connect to Wi-Fi or turn it off in Settings."

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeListViewModel(application: Application, private val feedId: Long) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val playbackController = PlaybackController.get(application)
    private val appSettings = AppSettings.get(application)

    val feed: StateFlow<Feed?> = repository.observeFeed(feedId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val playback: StateFlow<PlaybackUiState> = playbackController.state

    val downloadProgress: StateFlow<Map<Long, Float>> = repository.downloadProgress

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    private val visibleCount = MutableStateFlow(PAGE_SIZE)

    // Played episodes are filtered in the query so the page limit applies to what's actually shown.
    val episodes: StateFlow<List<Episode>> = combine(visibleCount, appSettings.hidePlayedEpisodes) { limit, hidePlayed ->
        limit to hidePlayed
    }.flatMapLatest { (limit, hidePlayed) -> repository.observeEpisodes(feedId, limit, hidePlayed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val totalEpisodeCount: StateFlow<Int> = repository.observeEpisodeCount(feedId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val matchingEpisodeCount: StateFlow<Int> = appSettings.hidePlayedEpisodes
        .flatMapLatest { hidePlayed -> repository.observeEpisodeCount(feedId, hidePlayed) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hasMoreEpisodes: StateFlow<Boolean> = combine(episodes, matchingEpisodeCount) { shown, total ->
        shown.size < total
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    /** True when the feed has episodes but the "hide played" setting is filtering all of them out. */
    val allHiddenAsPlayed: StateFlow<Boolean> = combine(appSettings.hidePlayedEpisodes, totalEpisodeCount) { hidePlayed, total ->
        hidePlayed && total > 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun loadMoreEpisodes() {
        visibleCount.value += PAGE_SIZE
    }

    fun reorderEpisodes(orderedEpisodeIds: List<Long>) {
        viewModelScope.launch { repository.reorderEpisodes(orderedEpisodeIds) }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refreshFeed(feedId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = e.message ?: e.javaClass.simpleName
                _errorMessages.emit("Failed to refresh \"${feed.value?.title ?: "podcast"}\": $reason")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun download(episode: Episode) {
        if (repository.isWifiBlockingDownloads()) {
            _errorMessages.tryEmit(WIFI_ONLY_MESSAGE)
            return
        }
        viewModelScope.launch { repository.downloadEpisode(episode) }
    }

    /** True if queueing [episode] will leave it undownloaded because Wi-Fi-only downloads is on and there's no Wi-Fi. */
    fun willSkipDownloadOnQueue(episode: Episode): Boolean =
        episode.downloadState != DownloadState.DOWNLOADED &&
            episode.downloadState != DownloadState.DOWNLOADING &&
            repository.isWifiBlockingDownloads()

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { repository.deleteDownload(episode) }
    }

    fun updateKeepLatestCount(count: Int) {
        viewModelScope.launch { repository.updateKeepLatestCount(feedId, count) }
    }

    fun removeFeed(onRemoved: () -> Unit) {
        viewModelScope.launch {
            repository.removeFeed(feedId)
            onRemoved()
        }
    }

    fun play(episode: Episode, autoPlay: Boolean = true) {
        viewModelScope.launch {
            val feedTitle = feed.value?.title ?: ""
            val source = episode.localFilePath
                ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                ?: episode.audioUrl
            playbackController.playEpisode(
                episodeId = episode.id,
                title = episode.title,
                artist = feedTitle,
                artworkUri = episode.imageUrl ?: feed.value?.imageUrl,
                uri = source,
                startPositionMs = episode.playbackPositionMs,
                autoPlay = autoPlay
            )
        }
    }

    /**
     * Starts playing the given episode (or resumes it if it's already the current one),
     * then opens the full-screen player.
     */
    fun openEpisode(episode: Episode, onOpenPlayer: () -> Unit) {
        val state = playback.value
        if (state.currentEpisodeId != episode.id) {
            play(episode)
        } else if (!state.isPlaying) {
            viewModelScope.launch { playbackController.togglePlayPause() }
        }
        onOpenPlayer()
    }

    fun addToQueue(episode: Episode) {
        viewModelScope.launch { repository.addToQueue(episode) }
    }

    fun removeFromQueue(episode: Episode) {
        viewModelScope.launch { repository.removeFromQueue(episode.id) }
    }

    fun markAsPlayed(episode: Episode) {
        viewModelScope.launch { repository.markAsPlayed(episode.id) }
    }
}
