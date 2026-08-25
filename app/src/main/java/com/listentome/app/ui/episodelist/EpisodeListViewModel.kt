package com.listentome.app.ui.episodelist

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

    val episodes: StateFlow<List<Episode>> = combine(
        visibleCount.flatMapLatest { limit -> repository.observeEpisodes(feedId, limit) },
        appSettings.hidePlayedEpisodes
    ) { episodes, hidePlayed ->
        if (hidePlayed) episodes.filterNot { it.isFinished } else episodes
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val totalEpisodeCount: StateFlow<Int> = repository.observeEpisodeCount(feedId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hasMoreEpisodes: StateFlow<Boolean> = combine(episodes, totalEpisodeCount) { shown, total ->
        shown.size < total
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
                _errorMessages.emit("Failed to refresh \"${feed.value?.title ?: "podcast"}\"")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun download(episode: Episode) {
        viewModelScope.launch { repository.downloadEpisode(episode) }
    }

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
     * Loads the given episode (without starting playback) unless it's already the current one,
     * then opens the full-screen player. Play/pause only happens from the player screen.
     */
    fun openEpisode(episode: Episode, onOpenPlayer: () -> Unit) {
        if (playback.value.currentEpisodeId != episode.id) {
            play(episode, autoPlay = false)
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
