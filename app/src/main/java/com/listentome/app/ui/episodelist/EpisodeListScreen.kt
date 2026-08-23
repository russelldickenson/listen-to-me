package com.listentome.app.ui.episodelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
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
import com.listentome.app.data.Feed
import com.listentome.app.ui.components.EpisodeArtwork
import com.listentome.app.ui.components.EpisodeInfoColumn
import com.listentome.app.ui.components.HtmlText
import kotlin.math.roundToInt

private val EpisodeRowHeight = 88.dp

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
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    var showSettings by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    var actionsEpisodeId by remember { mutableStateOf<Long?>(null) }

    var items by remember { mutableStateOf(episodes) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { EpisodeRowHeight.toPx() }

    LaunchedEffect(episodes) {
        if (draggedIndex == null) items = episodes
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = feed != null) { showDetails = true }
                    ) {
                        AsyncImage(
                            model = feed?.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(feed?.title ?: "", modifier = Modifier.padding(start = 8.dp))
                    }
                },
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
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No episodes yet. Pull to refresh once you're ready.")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                itemsIndexed(items, key = { _, episode -> episode.id }) { index, episode ->
                    val isDragging = index == draggedIndex
                    EpisodeRow(
                        episode = episode,
                        fallbackArtworkUrl = feed?.imageUrl,
                        downloadProgress = downloadProgress[episode.id],
                        isDragging = isDragging,
                        onPlayPauseClick = { viewModel.playOrToggle(episode, onOpenPlayer = onPlay) },
                        onOpenActions = { actionsEpisodeId = episode.id },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                            .pointerInput(episode.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggedIndex = items.indexOfFirst { it.id == episode.id }
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                        viewModel.reorderEpisodes(items.map { it.id })
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                        val from = draggedIndex ?: return@detectDragGesturesAfterLongPress
                                        val to = (from + (dragOffsetY / itemHeightPx).roundToInt())
                                            .coerceIn(0, items.lastIndex)
                                        if (to != from) {
                                            items = items.toMutableList().apply { add(to, removeAt(from)) }
                                            dragOffsetY -= (to - from) * itemHeightPx
                                            draggedIndex = to
                                        }
                                    }
                                )
                            }
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

    if (showDetails) {
        feed?.let { PodcastDetailsDialog(feed = it, onDismiss = { showDetails = false }) }
    }

    val actionsEpisode = actionsEpisodeId?.let { id -> episodes.find { it.id == id } }
    actionsEpisode?.let { episode ->
        EpisodeActionsSheet(
            episode = episode,
            downloadProgress = downloadProgress[episode.id],
            onDismiss = { actionsEpisodeId = null },
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
    downloadProgress: Float?,
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
                label = if (downloadProgress != null) "Downloading… ${(downloadProgress * 100).toInt()}%" else "Downloading…",
                enabled = false,
                onClick = {},
                supportingContent = {
                    if (downloadProgress != null) {
                        LinearProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
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
    onClick: () -> Unit,
    supportingContent: (@Composable () -> Unit)? = null
) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        supportingContent = supportingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun PodcastDetailsDialog(
    feed: Feed,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = feed.imageUrl,
                    contentDescription = feed.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Text(
                    feed.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
        },
        text = {
            HtmlText(
                html = feed.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
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
                Text("Auto-download episodes", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Automatically download the latest N episodes. Set to 0 to disable downloads.",
                    modifier = Modifier.padding(top = 4.dp)
                )
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

@Composable
private fun EpisodeRow(
    episode: Episode,
    fallbackArtworkUrl: String?,
    downloadProgress: Float?,
    isDragging: Boolean,
    onPlayPauseClick: () -> Unit,
    onOpenActions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(EpisodeRowHeight)
            .padding(bottom = 12.dp)
            .clickable(onClick = onPlayPauseClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                CardDefaults.cardColors().containerColor
            }
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EpisodeArtwork(
                imageUrl = episode.imageUrl ?: fallbackArtworkUrl,
                contentDescription = episode.title,
                downloadState = episode.downloadState,
                downloadProgress = downloadProgress,
                isFinished = episode.isFinished,
                isQueued = episode.queuePosition != null
            )
            EpisodeInfoColumn(
                title = episode.title,
                publishedAt = episode.publishedAt,
                playbackPositionMs = episode.playbackPositionMs,
                durationSeconds = episode.durationSeconds,
                isFinished = episode.isFinished,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            IconButton(onClick = onOpenActions) {
                Icon(Icons.Default.MoreVert, contentDescription = "Episode actions")
            }
        }
    }
}
