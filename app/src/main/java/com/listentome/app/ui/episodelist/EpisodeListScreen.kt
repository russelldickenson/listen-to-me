package com.listentome.app.ui.episodelist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.listentome.app.data.DownloadState
import com.listentome.app.data.Episode
import com.listentome.app.data.Feed
import com.listentome.app.ui.components.DownloadStatusRing
import com.listentome.app.ui.components.EpisodeArtwork
import com.listentome.app.ui.components.EpisodeInfoColumn
import com.listentome.app.ui.components.moved
import com.listentome.app.ui.components.performReorderSwap
import com.listentome.app.ui.components.reorderActions

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
    val playback by viewModel.playback.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val allHiddenAsPlayed by viewModel.allHiddenAsPlayed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var showSettings by remember { mutableStateOf(false) }
    var actionsEpisodeId by remember { mutableStateOf<Long?>(null) }

    val listState = rememberLazyListState()
    var items by remember { mutableStateOf(episodes) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(episodes) {
        if (draggedIndex == null) items = episodes
    }

    val scope = rememberCoroutineScope()
    fun toggleQueue(episode: Episode) {
        val wasQueued = episode.queuePosition != null
        val skipsDownload = !wasQueued && viewModel.willSkipDownloadOnQueue(episode)
        if (wasQueued) viewModel.removeFromQueue(episode) else viewModel.addToQueue(episode)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = when {
                    wasQueued -> "Removed from queue"
                    skipsDownload -> "Added to top of queue, not downloaded (Wi-Fi only)"
                    else -> "Added to top of queue"
                },
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (wasQueued) viewModel.addToQueue(episode) else viewModel.removeFromQueue(episode)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.errorMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = feed?.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Text(
                            feed?.title ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (items.isEmpty()) {
                    // Scrollable so pull-to-refresh can latch onto it.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (allHiddenAsPlayed) {
                                "No unplayed episodes. Turn off \"Hide played episodes\" in Settings to see played ones."
                            } else {
                                "No episodes yet. Pull down to refresh."
                            }
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(items, key = { _, episode -> episode.id }) { index, episode ->
                            val isDragging = index == draggedIndex
                            val isCurrentEpisode = playback.currentEpisodeId == episode.id
                            EpisodeRow(
                                episode = episode,
                                fallbackArtworkUrl = feed?.imageUrl,
                                downloadProgress = downloadProgress[episode.id],
                                isCurrentEpisode = isCurrentEpisode,
                                isPlaying = isCurrentEpisode && playback.isPlaying,
                                isBuffering = isCurrentEpisode && playback.isBuffering,
                                isDragging = isDragging,
                                onPlayPauseClick = { viewModel.openEpisode(episode, onOpenPlayer = onPlay) },
                                onOpenActions = { actionsEpisodeId = episode.id },
                                onToggleQueue = { toggleQueue(episode) },
                                modifier = Modifier
                                    .reorderActions(index, items.lastIndex) { from, to ->
                                        items = items.moved(from, to)
                                        viewModel.reorderEpisodes(items.map { it.id })
                                    }
                                    .zIndex(if (isDragging) 1f else 0f)
                                    .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                                    .pointerInput(episode.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
                                                performReorderSwap(listState, items, from, dragOffsetY)?.let { (newItems, newIndex, newOffset) ->
                                                    items = newItems
                                                    draggedIndex = newIndex
                                                    dragOffsetY = newOffset
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

    val actionsEpisode = actionsEpisodeId?.let { id -> episodes.find { it.id == id } }
    actionsEpisode?.let { episode ->
        EpisodeActionsSheet(
            episode = episode,
            downloadProgress = downloadProgress[episode.id],
            onDismiss = { actionsEpisodeId = null },
            onDownload = { viewModel.download(episode) },
            onDeleteDownload = { viewModel.deleteDownload(episode) },
            onMarkAsPlayed = { viewModel.markAsPlayed(episode) },
            onToggleQueue = { toggleQueue(episode) }
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
private fun FeedSettingsDialog(
    feedTitle: String,
    currentKeepCount: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit,
    onRemoveFeed: () -> Unit
) {
    var enabled by remember { mutableStateOf(currentKeepCount > 0) }
    var count by remember { mutableStateOf(if (currentKeepCount > 0) currentKeepCount else 3) }
    var showRemoveConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Feed settings") },
        text = {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { enabled = !enabled },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Auto-download episodes",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f).padding(end = 8.dp)
                    )
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { count = (count - 1).coerceAtLeast(1) },
                        enabled = enabled && count > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease")
                    }
                    Text(
                        "$count episodes",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    IconButton(onClick = { count++ }, enabled = enabled) {
                        Icon(Icons.Default.Add, contentDescription = "Increase")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRemoveConfirm = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        "Remove this podcast",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(if (enabled) count else 0) }) { Text("Save") }
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
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isDragging: Boolean,
    onPlayPauseClick: () -> Unit,
    onOpenActions: () -> Unit,
    onToggleQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
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
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            EpisodeArtwork(
                imageUrl = episode.imageUrl ?: fallbackArtworkUrl,
                contentDescription = null,
                downloadState = episode.downloadState,
                downloadProgress = downloadProgress,
                isFinished = episode.isFinished,
                isQueued = episode.queuePosition != null,
                isCurrentEpisode = isCurrentEpisode,
                isPlaying = isPlaying,
                isBuffering = isBuffering
            )
            EpisodeInfoColumn(
                title = episode.title,
                publishedAt = episode.publishedAt,
                isFinished = episode.isFinished,
                positionMs = episode.playbackPositionMs,
                durationSeconds = episode.durationSeconds,
                isDownloaded = episode.downloadState == DownloadState.DOWNLOADED,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            val isQueued = episode.queuePosition != null
            IconButton(onClick = onToggleQueue) {
                Icon(
                    imageVector = if (isQueued) Icons.AutoMirrored.Filled.PlaylistAddCheck else Icons.AutoMirrored.Filled.PlaylistAdd,
                    contentDescription = if (isQueued) "Remove from queue" else "Add to queue",
                    tint = if (isQueued) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
            Box(contentAlignment = Alignment.Center) {
                DownloadStatusRing(downloadState = episode.downloadState, downloadProgress = downloadProgress)
                IconButton(onClick = onOpenActions) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Episode actions")
                }
            }
        }
    }
}
