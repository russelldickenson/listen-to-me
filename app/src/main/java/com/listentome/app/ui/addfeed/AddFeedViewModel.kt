package com.listentome.app.ui.addfeed

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.repository.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AddFeedUiState {
    data object Idle : AddFeedUiState
    data object Loading : AddFeedUiState
    data class Error(val message: String) : AddFeedUiState
    data class Success(val feedId: Long) : AddFeedUiState
}

class AddFeedViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)

    private val _uiState = MutableStateFlow<AddFeedUiState>(AddFeedUiState.Idle)
    val uiState: StateFlow<AddFeedUiState> = _uiState.asStateFlow()

    fun addFeed(url: String) {
        if (url.isBlank()) {
            _uiState.value = AddFeedUiState.Error("Enter an RSS feed URL")
            return
        }
        viewModelScope.launch {
            _uiState.value = AddFeedUiState.Loading
            try {
                val feed = repository.addFeed(url)
                _uiState.value = AddFeedUiState.Success(feed.id)
            } catch (e: Exception) {
                _uiState.value = AddFeedUiState.Error(e.message ?: "Could not add feed")
            }
        }
    }
}
