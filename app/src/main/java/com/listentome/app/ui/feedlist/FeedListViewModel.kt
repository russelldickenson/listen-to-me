package com.listentome.app.ui.feedlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.data.Feed
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val STALE_THRESHOLD_MS = 24 * 60 * 60 * 1000L

class FeedListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)

    val feeds: StateFlow<List<Feed>> = repository.observeFeeds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
                repository.refreshAllFeeds()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun removeFeed(feedId: Long) {
        viewModelScope.launch { repository.removeFeed(feedId) }
    }

    fun reorderFeeds(orderedFeedIds: List<Long>) {
        viewModelScope.launch { repository.reorderFeeds(orderedFeedIds) }
    }
}
