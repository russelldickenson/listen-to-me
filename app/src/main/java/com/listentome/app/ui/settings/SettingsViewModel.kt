package com.listentome.app.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.listentome.app.repository.OpmlImportResult
import com.listentome.app.repository.PodcastRepository
import com.listentome.app.settings.AppSettings
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SettingsMessage {
    data class ExportFailed(val reason: String) : SettingsMessage
    data class ImportComplete(val result: OpmlImportResult) : SettingsMessage
    data class ImportFailed(val reason: String) : SettingsMessage
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PodcastRepository.get(application)
    private val appSettings = AppSettings.get(application)

    private val _message = MutableStateFlow<SettingsMessage?>(null)
    val message: StateFlow<SettingsMessage?> = _message.asStateFlow()

    val totalDownloadBytes: StateFlow<Long> = repository.observeDownloadedEpisodes()
        .map { episodes ->
            episodes.sumOf { episode ->
                episode.localFilePath?.let { File(it).takeIf(File::exists)?.length() } ?: 0L
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val autoplayQueueEnabled: StateFlow<Boolean> = appSettings.autoplayQueueEnabled

    fun setAutoplayQueueEnabled(enabled: Boolean) {
        appSettings.setAutoplayQueueEnabled(enabled)
    }

    val skipForwardSeconds: StateFlow<Int> = appSettings.skipForwardSeconds

    fun setSkipForwardSeconds(seconds: Int) {
        appSettings.setSkipForwardSeconds(seconds)
    }

    val skipBackSeconds: StateFlow<Int> = appSettings.skipBackSeconds

    fun setSkipBackSeconds(seconds: Int) {
        appSettings.setSkipBackSeconds(seconds)
    }

    val autoSkipBackOnResume: StateFlow<Boolean> = appSettings.autoSkipBackOnResume

    fun setAutoSkipBackOnResume(enabled: Boolean) {
        appSettings.setAutoSkipBackOnResume(enabled)
    }

    fun clearMessage() {
        _message.value = null
    }

    fun exportOpml(uri: Uri) {
        viewModelScope.launch {
            try {
                val opml = repository.exportOpml()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(opml.toByteArray())
                } ?: throw IllegalStateException("Could not open destination for writing")
            } catch (e: Exception) {
                _message.value = SettingsMessage.ExportFailed(e.message ?: "Unknown error")
            }
        }
    }

    fun importOpml(uri: Uri) {
        viewModelScope.launch {
            try {
                val input = getApplication<Application>().contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open file for reading")
                val result = repository.importOpml(input)
                _message.value = SettingsMessage.ImportComplete(result)
            } catch (e: Exception) {
                _message.value = SettingsMessage.ImportFailed(e.message ?: "Unknown error")
            }
        }
    }
}
