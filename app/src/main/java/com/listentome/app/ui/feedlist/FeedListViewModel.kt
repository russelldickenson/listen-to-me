package com.listentome.app.ui.feedlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Feed
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L

class FeedListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)

    val feeds: StateFlow<List<Feed>> = repository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _refreshProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    /** (completed, total) podcasts refreshed so far, or null when not refreshing. */
    val refreshProgress: StateFlow<Pair<Int, Int>?> = _refreshProgress.asStateFlow()

    private val _errorMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errorMessages: SharedFlow<String> = _errorMessages.asSharedFlow()

    val hasStaleFeeds: StateFlow<Boolean> = feeds
        .map { list ->
            val now = System.currentTimeMillis()
            list.isNotEmpty() && list.any { feed ->
                feed.lastRefreshedAt == null || now - feed.lastRefreshedAt > STALE_THRESHOLD_MS
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun refreshAll() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val failed = repository.refreshAllFeeds { completed, total ->
                    _refreshProgress.value = completed to total
                }
                if (failed.isNotEmpty()) {
                    _errorMessages.emit(
                        if (failed.size == 1) {
                            "Failed to refresh \"${failed[0]}\""
                        } else {
                            "Failed to refresh ${failed.size} podcasts"
                        }
                    )
                }
            } finally {
                _isRefreshing.value = false
            }
            delay(1000)
            _refreshProgress.value = null
        }
    }

    fun removeFeed(feedId: Long) {
        viewModelScope.launch { repository.removeFeed(feedId) }
    }

    fun reorderFeeds(orderedFeedIds: List<Long>) {
        viewModelScope.launch { repository.reorderFeeds(orderedFeedIds) }
    }
}
