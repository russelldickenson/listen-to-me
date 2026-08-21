package com.listentome.app.ui.addfeed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.network.ParsedFeed
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AddFeedUiState {
    data object Idle : AddFeedUiState
    data object Loading : AddFeedUiState
    data class Preview(val url: String, val parsed: ParsedFeed) : AddFeedUiState
    data class Error(val message: String) : AddFeedUiState
    data class Success(val feedId: Long) : AddFeedUiState
}

class AddFeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)

    private val _uiState = MutableStateFlow<AddFeedUiState>(AddFeedUiState.Idle)
    val uiState: StateFlow<AddFeedUiState> = _uiState.asStateFlow()

    fun fetchPreview(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            _uiState.value = AddFeedUiState.Error("Enter an RSS feed URL")
            return
        }
        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            try {
                val parsed = repository.previewFeed(trimmed)
                _uiState.value = AddFeedUiState.Preview(trimmed, parsed)
            } catch (e: Exception) {
                _uiState.value = AddFeedUiState.Error(e.message ?: "Could not load feed")
            }
        }
    }

    fun confirmAdd() {
        val state = _uiState.value
        if (state !is AddFeedUiState.Preview) return
        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            try {
                val feed = repository.addFeed(state.url, state.parsed)
                _uiState.value = AddFeedUiState.Success(feed.id)
            } catch (e: Exception) {
                _uiState.value = AddFeedUiState.Error(e.message ?: "Could not add feed")
            }
        }
    }

    fun cancelPreview() {
        _uiState.value = AddFeedUiState.Idle
    }
}
