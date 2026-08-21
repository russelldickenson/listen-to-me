package com.listentome.app.ui.episodelist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.playback.PlaybackController
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EpisodeListViewModel(application: Application, private val feedId: Long) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val playbackController = PlaybackController.get(application)

    val feed: StateFlow<Feed?> = repository.observeFeed(feedId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val episodes: StateFlow<List<Episode>> = repository.observeEpisodes(feedId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun refresh() {
        viewModelScope.launch { repository.refreshFeed(feedId) }
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

    fun play(episode: Episode) {
        viewModelScope.launch {
            val feedTitle = feed.value?.title ?: ""
            val source = episode.localFilePath
                ?.let { android.net.Uri.fromFile(java.io.File(it)).toString() }
                ?: episode.audioUrl
            playbackController.playEpisode(
                episodeId = episode.id,
                title = episode.title,
                artist = feedTitle,
                artworkUri = feed.value?.imageUrl,
                uri = source,
                startPositionMs = episode.playbackPositionMs
            )
        }
    }
}
