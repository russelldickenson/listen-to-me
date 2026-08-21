package com.listentome.app.ui.episodelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.listentome.app.data.DownloadState
import com.listentome.app.data.Episode
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListScreen(
    viewModel: EpisodeListViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val feed by viewModel.feed.collectAsState()
    val episodes by viewModel.episodes.collectAsState()
    val hasMoreEpisodes by viewModel.hasMoreEpisodes.collectAsState()
    val playback by viewModel.playback.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var actionsEpisode by remember { mutableStateOf<Episode?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feed?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Feed settings")
                    }
                }
            )
        }
    ) { padding ->
        if (episodes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No episodes yet. Pull to refresh once you're ready.")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                items(episodes, key = { it.id }) { episode ->
                    val isCurrentEpisode = playback.currentEpisodeId == episode.id
                    EpisodeRow(
                        episode = episode,
                        fallbackArtworkUrl = feed?.imageUrl,
                        isPlaying = isCurrentEpisode && playback.isPlaying,
                        onPlay = { viewModel.playOrToggle(episode, onStartedNewEpisode = onPlay) },
                        onDownload = { viewModel.download(episode) },
                        onDeleteDownload = { viewModel.deleteDownload(episode) },
                        onLongPress = { actionsEpisode = episode }
                    )
                }
                if (hasMoreEpisodes) {
                    item {
                        TextButton(
                            onClick = viewModel::loadMoreEpisodes,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("More episodes…")
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        FeedSettingsDialog(
            currentKeepCount = feed?.keepLatestCount ?: 0,
            onDismiss = { showSettings = false },
            onSave = { count ->
                viewModel.updateKeepLatestCount(count)
                showSettings = false
            },
            onRemoveFeed = {
                viewModel.removeFeed(onBack)
                showSettings = false
            }
        )
    }

    actionsEpisode?.let { episode ->
        EpisodeActionsDialog(
            episode = episode,
            onDismiss = { actionsEpisode = null },
            onMarkAsPlayed = { viewModel.markAsPlayed(episode) },
            onToggleQueue = {
                if (episode.queuePosition != null) {
                    viewModel.removeFromQueue(episode)
                } else {
                    viewModel.addToQueue(episode)
                }
            }
        )
    }
}

@Composable
private fun EpisodeActionsDialog(
    episode: Episode,
    onDismiss: () -> Unit,
    onMarkAsPlayed: () -> Unit,
    onToggleQueue: () -> Unit
) {
    val isQueued = episode.queuePosition != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(episode.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                TextButton(
                    onClick = { onMarkAsPlayed(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Mark as played", modifier = Modifier.fillMaxWidth())
                }
                TextButton(
                    onClick = { onToggleQueue(); onDismiss() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (isQueued) "Remove from queue" else "Add to queue",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun FeedSettingsDialog(
    currentKeepCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemoveFeed: () -> Unit
) {
    var text by remember { mutableStateOf(currentKeepCount.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Feed settings") },
        text = {
            Column {
                Text("Automatically download the latest N episodes. Set to 0 to disable downloads.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter { c -> c.isDigit() } },
                    label = { Text("Episodes to keep downloaded") },
                    modifier = Modifier.padding(top = 8.dp)
                )
                TextButton(onClick = onRemoveFeed, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Remove this podcast", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.toIntOrNull() ?: 0) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: Episode,
    fallbackArtworkUrl: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = episode.imageUrl ?: fallbackArtworkUrl,
                contentDescription = episode.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text(episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatDate(episode.publishedAt), style = MaterialTheme.typography.bodySmall)
                if (episode.isFinished) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Played", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (episode.queuePosition != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("In queue", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
            when (episode.downloadState) {
                DownloadState.NOT_DOWNLOADED -> IconButton(onClick = onDownload) {
                    Icon(Icons.Default.Download, contentDescription = "Download")
                }
                DownloadState.DOWNLOADING -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                DownloadState.DOWNLOADED -> IconButton(onClick = onDeleteDownload) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete download")
                }
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(epochMillis))
}
