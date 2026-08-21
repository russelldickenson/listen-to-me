package com.listentome.app.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class PlaybackUiState(
    val currentEpisodeId: Long? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0
)

class PlaybackController private constructor(private val context: Context) {

    private var controller: MediaController? = null
    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val positionPoller = CoroutineScope(Dispatchers.Main)
    private var pollJob: Job? = null

    private suspend fun ensureController(): MediaController {
        controller?.let { return it }
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        return suspendCancellableCoroutine { cont ->
            future.addListener({
                val c = future.get()
                c.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.value = _state.value.copy(isPlaying = isPlaying)
                    }

                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                        val episodeId = mediaItem?.mediaId?.toLongOrNull()
                        _state.value = _state.value.copy(currentEpisodeId = episodeId, positionMs = 0)
                    }
                })
                controller = c
                cont.resume(c)
            }, MoreExecutors.directExecutor())
        }
    }

    suspend fun playEpisode(episodeId: Long, title: String, artist: String, artworkUri: String?, uri: String, startPositionMs: Long) {
        val c = ensureController()
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply { artworkUri?.let { setArtworkUri(android.net.Uri.parse(it)) } }
            .build()
        val mediaItem = MediaItem.Builder()
            .setMediaId(episodeId.toString())
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
        c.setMediaItem(mediaItem, startPositionMs)
        c.prepare()
        c.playWhenReady = true
        _state.value = _state.value.copy(currentEpisodeId = episodeId, positionMs = startPositionMs)
        startPolling()
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = positionPoller.launch {
            while (true) {
                val c = controller
                if (c != null) {
                    _state.value = _state.value.copy(
                        positionMs = c.currentPosition.coerceAtLeast(0),
                        durationMs = c.duration.coerceAtLeast(0)
                    )
                }
                delay(500)
            }
        }
    }

    suspend fun togglePlayPause() {
        val c = ensureController()
        if (c.isPlaying) c.pause() else c.play()
    }

    suspend fun seekTo(positionMs: Long) {
        val c = ensureController()
        c.seekTo(positionMs)
    }

    suspend fun seekBy(deltaMs: Long) {
        val c = ensureController()
        val target = (c.currentPosition + deltaMs).coerceIn(0, c.duration.coerceAtLeast(0))
        c.seekTo(target)
    }

    fun currentPositionMs(): Long = controller?.currentPosition ?: 0

    companion object {
        @Volatile
        private var instance: PlaybackController? = null

        fun get(context: Context): PlaybackController =
            instance ?: synchronized(this) {
                instance ?: PlaybackController(context.applicationContext).also { instance = it }
            }
    }
}
