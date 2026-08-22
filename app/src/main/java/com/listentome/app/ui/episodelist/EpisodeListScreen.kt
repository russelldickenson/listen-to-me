package com.listentome.app.ui.episodelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
                        onOpenPlayer = onPlay,
                        onPlayPauseClick = { viewModel.playOrToggle(episode, onOpenPlayer = onPlay) },
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
            feedTitle = feed?.title ?: "",
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
        EpisodeActionsSheet(
            episode = episode,
            onDismiss = { actionsEpisode = null },
            onDownload = { viewModel.download(episode) },
            onDeleteDownload = { viewModel.deleteDownload(episode) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeActionsSheet(
    episode: Episode,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onMarkAsPlayed: () -> Unit,
    onToggleQueue: () -> Unit
) {
    val isQueued = episode.queuePosition != null
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            episode.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        HorizontalDivider()

        when (episode.downloadState) {
            DownloadState.NOT_DOWNLOADED -> EpisodeActionItem(
                icon = Icons.Default.Download,
                label = "Download episode",
                onClick = { onDownload(); onDismiss() }
            )
            DownloadState.DOWNLOADING -> EpisodeActionItem(
                icon = Icons.Default.Download,
                label = "Downloading…",
                enabled = false,
                onClick = {}
            )
            DownloadState.DOWNLOADED -> EpisodeActionItem(
                icon = Icons.Default.Delete,
                label = "Remove download",
                onClick = { onDeleteDownload(); onDismiss() }
            )
            DownloadState.FAILED -> EpisodeActionItem(
                icon = Icons.Default.Refresh,
                label = "Retry download",
                onClick = { onDownload(); onDismiss() }
            )
        }

        EpisodeActionItem(
            icon = Icons.Default.CheckCircle,
            label = "Mark as played",
            enabled = !episode.isFinished,
            onClick = { onMarkAsPlayed(); onDismiss() }
        )

        EpisodeActionItem(
            icon = if (isQueued) Icons.Default.PlaylistRemove else Icons.AutoMirrored.Filled.PlaylistAdd,
            label = if (isQueued) "Remove from queue" else "Add to queue",
            onClick = { onToggleQueue(); onDismiss() }
        )
    }
}

@Composable
private fun EpisodeActionItem(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun FeedSettingsDialog(
    feedTitle: String,
    currentKeepCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemoveFeed: () -> Unit
) {
    var count by remember { mutableStateOf(currentKeepCount) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Feed settings") },
        text = {
            Column {
                Text("Automatically download the latest N episodes. Set to 0 to disable downloads.")
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (count > 0) count-- }, enabled = count > 0) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(onClick = { count++ }) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

                ListItem(
                    headlineContent = { Text("Remove this podcast", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRemoveConfirm = true }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(count) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showRemoveConfirm) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirm = false },
            title = { Text("Remove podcast?") },
            text = { Text("This removes \"$feedTitle\" and deletes any downloaded episodes. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = onRemoveFeed,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: Episode,
    fallbackArtworkUrl: String?,
    isPlaying: Boolean,
    onOpenPlayer: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .combinedClickable(onClick = onOpenPlayer, onLongClick = onLongPress)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                AsyncImage(
                    model = episode.imageUrl ?: fallbackArtworkUrl,
                    contentDescription = episode.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                if (episode.downloadState == DownloadState.DOWNLOADED) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Downloaded",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(3.dp)
                    )
                }
                if (episode.downloadState == DownloadState.FAILED) {
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = "Download failed",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape)
                            .padding(3.dp)
                    )
                }
            }
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
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play"
                )
            }
        }
    }
}

private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(epochMillis))
}
