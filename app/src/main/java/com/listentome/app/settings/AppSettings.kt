package com.listentome.app.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _autoplayQueueEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTOPLAY_QUEUE, false))
    val autoplayQueueEnabled: StateFlow<Boolean> = _autoplayQueueEnabled.asStateFlow()

    fun setAutoplayQueueEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_QUEUE, enabled).apply()
        _autoplayQueueEnabled.value = enabled
    }

    private val _skipForwardSeconds = MutableStateFlow(prefs.getInt(KEY_SKIP_FORWARD_SECONDS, DEFAULT_SKIP_FORWARD_SECONDS))
    val skipForwardSeconds: StateFlow<Int> = _skipForwardSeconds.asStateFlow()

    fun setSkipForwardSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_FORWARD_SECONDS, seconds).apply()
        _skipForwardSeconds.value = seconds
    }

    private val _skipBackSeconds = MutableStateFlow(prefs.getInt(KEY_SKIP_BACK_SECONDS, DEFAULT_SKIP_BACK_SECONDS))
    val skipBackSeconds: StateFlow<Int> = _skipBackSeconds.asStateFlow()

    fun setSkipBackSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_SKIP_BACK_SECONDS, seconds).apply()
        _skipBackSeconds.value = seconds
    }

    private val _autoSkipBackOnResume = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SKIP_BACK_ON_RESUME, false))
    val autoSkipBackOnResume: StateFlow<Boolean> = _autoSkipBackOnResume.asStateFlow()

    fun setAutoSkipBackOnResume(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SKIP_BACK_ON_RESUME, enabled).apply()
        _autoSkipBackOnResume.value = enabled
    }

    private val _wifiOnlyDownloads = MutableStateFlow(prefs.getBoolean(KEY_WIFI_ONLY_DOWNLOADS, true))
    val wifiOnlyDownloads: StateFlow<Boolean> = _wifiOnlyDownloads.asStateFlow()

    fun setWifiOnlyDownloads(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WIFI_ONLY_DOWNLOADS, enabled).apply()
        _wifiOnlyDownloads.value = enabled
    }

    private val _defaultKeepLatestCount = MutableStateFlow(prefs.getInt(KEY_DEFAULT_KEEP_LATEST_COUNT, DEFAULT_KEEP_LATEST_COUNT))
    val defaultKeepLatestCount: StateFlow<Int> = _defaultKeepLatestCount.asStateFlow()

    fun setDefaultKeepLatestCount(count: Int) {
        prefs.edit().putInt(KEY_DEFAULT_KEEP_LATEST_COUNT, count).apply()
        _defaultKeepLatestCount.value = count
    }

    /** Max total bytes of downloaded episodes to keep on disk. 0 means unlimited. */
    private val _maxDownloadStorageBytes = MutableStateFlow(prefs.getLong(KEY_MAX_DOWNLOAD_STORAGE_BYTES, DEFAULT_MAX_DOWNLOAD_STORAGE_BYTES))
    val maxDownloadStorageBytes: StateFlow<Long> = _maxDownloadStorageBytes.asStateFlow()

    fun setMaxDownloadStorageBytes(bytes: Long) {
        prefs.edit().putLong(KEY_MAX_DOWNLOAD_STORAGE_BYTES, bytes).apply()
        _maxDownloadStorageBytes.value = bytes
    }

    private val _autoDeletePlayedEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_DELETE_PLAYED, false))
    val autoDeletePlayedEnabled: StateFlow<Boolean> = _autoDeletePlayedEnabled.asStateFlow()

    fun setAutoDeletePlayedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_DELETE_PLAYED, enabled).apply()
        _autoDeletePlayedEnabled.value = enabled
    }

    companion object {
        private const val KEY_AUTOPLAY_QUEUE = "autoplay_queue_enabled"
        private const val KEY_SKIP_FORWARD_SECONDS = "skip_forward_seconds"
        private const val KEY_SKIP_BACK_SECONDS = "skip_back_seconds"
        private const val KEY_AUTO_SKIP_BACK_ON_RESUME = "auto_skip_back_on_resume"
        private const val KEY_WIFI_ONLY_DOWNLOADS = "wifi_only_downloads"
        private const val KEY_DEFAULT_KEEP_LATEST_COUNT = "default_keep_latest_count"
        private const val KEY_MAX_DOWNLOAD_STORAGE_BYTES = "max_download_storage_bytes"
        private const val KEY_AUTO_DELETE_PLAYED = "auto_delete_played_enabled"
        const val DEFAULT_SKIP_FORWARD_SECONDS = 30
        const val DEFAULT_SKIP_BACK_SECONDS = 15
        const val AUTO_SKIP_BACK_ON_RESUME_SECONDS = 3
        const val DEFAULT_KEEP_LATEST_COUNT = 3
        const val DEFAULT_MAX_DOWNLOAD_STORAGE_BYTES = 0L

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
