package com.listentome.app.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit
) {
    val playback by viewModel.playback.collectAsState()
    val episode by viewModel.currentEpisode.collectAsState()
    val feed by viewModel.currentFeed.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val currentEpisode = episode
        if (currentEpisode == null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
                Text("Nothing is playing yet.")
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AsyncImage(
                    model = currentEpisode.imageUrl ?: feed?.imageUrl,
                    contentDescription = currentEpisode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(240.dp)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Text(currentEpisode.title, style = MaterialTheme.typography.titleLarge)

                Slider(
                    value = playback.positionMs.toFloat().coerceAtMost(playback.durationMs.toFloat().coerceAtLeast(1f)),
                    valueRange = 0f..playback.durationMs.toFloat().coerceAtLeast(1f),
                    onValueChange = { viewModel.seekTo(it.toLong()) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(formatMillis(playback.positionMs))
                    Text(formatMillis(playback.durationMs), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                }

                Row(
                    modifier = Modifier.padding(top = 24.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.skip(-10_000) }) {
                        Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Icon(
                            imageVector = if (playback.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.skip(30_000) }) {
                        Icon(Icons.Default.Forward30, contentDescription = "Forward 30 seconds", modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

private fun formatMillis(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
