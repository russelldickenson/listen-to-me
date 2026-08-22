package com.listentome.app.ui.downloads

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.repository.PodcastRepository
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DownloadedEpisodeInfo(val episode: Episode, val feed: Feed?, val sizeBytes: Long)

class DownloadsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)

    val downloads: StateFlow<List<DownloadedEpisodeInfo>> = combine(
        repository.observeDownloadedEpisodes(),
        repository.observeFeeds()
    ) { episodes, feeds ->
        val feedsById = feeds.associateBy { it.id }
        episodes.map { episode ->
            val size = episode.localFilePath?.let { File(it).takeIf(File::exists)?.length() } ?: 0L
            DownloadedEpisodeInfo(episode, feedsById[episode.feedId], size)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteDownload(episode: Episode) {
        viewModelScope.launch { repository.deleteDownload(episode) }
    }
}
