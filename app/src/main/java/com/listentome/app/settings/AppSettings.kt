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

    companion object {
        private const val KEY_AUTOPLAY_QUEUE = "autoplay_queue_enabled"

        @Volatile
        private var instance: AppSettings? = null

        fun get(context: Context): AppSettings =
            instance ?: synchronized(this) {
                instance ?: AppSettings(context.applicationContext).also { instance = it }
            }
    }
}
