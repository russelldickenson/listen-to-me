package com.listentome.app.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.listentome.app.ui.components.ReorderHintBanner
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberCoroutineScope
import com.listentome.app.data.Episode
import kotlinx.coroutines.launch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.listentome.app.data.DownloadState
import com.listentome.app.ui.components.DownloadStatusRing
import com.listentome.app.ui.components.EpisodeArtwork
import com.listentome.app.ui.components.EpisodeInfoColumn
import com.listentome.app.ui.components.moved
import com.listentome.app.ui.components.performReorderSwap
import com.listentome.app.ui.components.reorderActions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onBrowsePodcasts: () -> Unit,
    onPlay: () -> Unit
) {
    val queue by viewModel.queue.collectAsState()
    val playback by viewModel.playback.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val reorderHintDismissed by viewModel.reorderHintDismissed.collectAsState()
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hasStaleFeeds by viewModel.hasStaleFeeds.collectAsState()
    val refreshProgress by viewModel.refreshProgress.collectAsState()

    LaunchedEffect(viewModel) {
        viewModel.errorMessages.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    LaunchedEffect(hasStaleFeeds) {
        if (hasStaleFeeds) {
            val result = snackbarHostState.showSnackbar(
                message = "Episodes might be out of date",
                actionLabel = "Refresh",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.refreshAll()
        }
    }

    fun removeWithUndo(episode: Episode) {
        viewModel.removeFromQueue(episode)
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = "Removed from queue",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.restoreToQueue(episode)
        }
    }

    val listState = rememberLazyListState()
    var items by remember { mutableStateOf(queue) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    LaunchedEffect(queue) {
        if (draggedIndex == null) items = queue
    }

    LaunchedEffect(draggedIndex) {
        if (draggedIndex == null) return@LaunchedEffect
        while (true) {
            val currentDragged = draggedIndex ?: break
            val layoutInfo = listState.layoutInfo
            val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentDragged }
            if (visibleItem != null) {
                val itemSize = visibleItem.size.toFloat()
                val scrollThresholdPx = itemSize * 0.75f
                val maxScrollSpeedPx = itemSize * 0.15f
                val visualTop = visibleItem.offset + dragOffsetY
                val visualBottom = visualTop + visibleItem.size
                val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                val viewportEnd = layoutInfo.viewportEndOffset.toFloat()

                var scrollDelta = 0f
                if (visualTop < viewportStart + scrollThresholdPx && listState.canScrollBackward) {
                    val factor = ((viewportStart + scrollThresholdPx - visualTop) / scrollThresholdPx).coerceIn(0f, 2f)
                    scrollDelta = -maxScrollSpeedPx * factor
                } else if (visualBottom > viewportEnd - scrollThresholdPx && listState.canScrollForward) {
                    val factor = ((visualBottom - (viewportEnd - scrollThresholdPx)) / scrollThresholdPx).coerceIn(0f, 2f)
                    scrollDelta = maxScrollSpeedPx * factor
                }

                if (scrollDelta != 0f) {
                    val consumed = listState.scrollBy(scrollDelta)
                    if (consumed != 0f) {
                        dragOffsetY -= consumed
                        performReorderSwap(listState, items, currentDragged, dragOffsetY)?.let { (newItems, newIndex, newOffset) ->
                            items = newItems
                            draggedIndex = newIndex
                            dragOffsetY = newOffset
                        }
                        if (draggedIndex == 0 && !listState.canScrollBackward) {
                            dragOffsetY = dragOffsetY.coerceAtMost(0f)
                        } else if (draggedIndex == items.lastIndex && !listState.canScrollForward) {
                            dragOffsetY = dragOffsetY.coerceAtMost(0f)
                        }
                    }
                }
            }
            delay(16)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text("Queue")
                        if (items.isNotEmpty()) {
                            val countText = if (items.size == 1) "1 episode" else "${items.size} episodes"
                            Text(
                                text = countText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
            val progress = refreshProgress
            if (progress != null) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { if (progress.second > 0) progress.first.toFloat() / progress.second else 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Your queue is empty. Tap the queue button on an episode to add it, " +
                        "or turn on \"Auto-queue downloads\" in Settings to add new episodes automatically.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                androidx.compose.material3.Button(
                    onClick = onBrowsePodcasts,
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Browse podcasts")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (!reorderHintDismissed && items.size >= 2) {
                    ReorderHintBanner(
                        onDismiss = viewModel::dismissReorderHint,
                        text = "Tip: Long-press and drag any episode to reorder the queue."
                    )
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    itemsIndexed(items, key = { _, item -> item.episode.id }) { index, item ->
                        val isDragging = index == draggedIndex
                        val isCurrentEpisode = playback.currentEpisodeId == item.episode.id
                        QueueRow(
                            item = item,
                            downloadProgress = downloadProgress[item.episode.id],
                            isCurrentEpisode = isCurrentEpisode,
                            isPlaying = isCurrentEpisode && playback.isPlaying,
                            isBuffering = isCurrentEpisode && playback.isBuffering,
                            isDragging = isDragging,
                            onPlay = { viewModel.openEpisode(item, onOpenPlayer = onPlay) },
                            onRemove = { removeWithUndo(item.episode) },
                            onDownload = { viewModel.download(item.episode) },
                            onDeleteDownload = { viewModel.deleteDownload(item.episode) },
                            onMarkAsPlayed = { viewModel.markAsPlayed(item.episode) },
                            reorderActions = Modifier.reorderActions(index, items.lastIndex) { from, to ->
                                items = items.moved(from, to)
                                viewModel.reorderQueue(items.map { it.episode.id })
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) {
                                        if (index == 0 && !listState.canScrollBackward) dragOffsetY.coerceAtLeast(0f)
                                        else if (index == items.lastIndex && !listState.canScrollForward) dragOffsetY.coerceAtMost(0f)
                                        else dragOffsetY
                                    } else 0f
                                }
                                .pointerInput(item.episode.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            draggedIndex = items.indexOfFirst { it.episode.id == item.episode.id }
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                            viewModel.reorderQueue(items.map { it.episode.id })
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                            items = queue
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
                                            if (draggedIndex == 0 && !listState.canScrollBackward) {
                                                dragOffsetY = dragOffsetY.coerceAtLeast(0f)
                                            } else if (draggedIndex == items.lastIndex && !listState.canScrollForward) {
                                                dragOffsetY = dragOffsetY.coerceAtMost(0f)
                                            }
                                        }
                                    )
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    downloadProgress: Float?,
    isCurrentEpisode: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isDragging: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onMarkAsPlayed: () -> Unit,
    reorderActions: Modifier,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CardDefaults.shape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove from queue",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
    Card(
        modifier = Modifier.fillMaxWidth().then(reorderActions).clickable(onClick = onPlay),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                CardDefaults.cardColors().containerColor
            }
        )
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .height(56.dp)
                    .width(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight(0.4f)
                                .width(1.5.dp)
                                .clip(RoundedCornerShape(0.75.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.weight(1f).padding(end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                EpisodeArtwork(
                    imageUrl = item.episode.imageUrl ?: item.feed?.imageUrl,
                    contentDescription = null,
                    downloadState = item.episode.downloadState,
                    downloadProgress = downloadProgress,
                    isFinished = item.episode.isFinished,
                    isQueued = item.episode.queuePosition != null,
                    isCurrentEpisode = isCurrentEpisode,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering
                )
                EpisodeInfoColumn(
                    title = item.episode.title,
                    publishedAt = item.episode.publishedAt,
                    isFinished = item.episode.isFinished,
                    subtitle = item.feed?.title,
                    positionMs = item.episode.playbackPositionMs,
                    durationSeconds = item.episode.durationSeconds,
                    isDownloaded = item.episode.downloadState == DownloadState.DOWNLOADED,
                    modifier = Modifier.weight(1f).padding(start = 12.dp)
                )
                if (item.episode.playbackPositionMs > 0 && !item.episode.isFinished && !isCurrentEpisode) {
                    IconButton(onClick = onPlay) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                }
                var showMenu by remember { mutableStateOf(false) }
                Box(contentAlignment = Alignment.Center) {
                    DownloadStatusRing(downloadState = item.episode.downloadState, downloadProgress = downloadProgress)
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Episode actions")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        when (item.episode.downloadState) {
                            DownloadState.NOT_DOWNLOADED -> DropdownMenuItem(
                                text = { Text("Download episode") },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = { showMenu = false; onDownload() }
                            )
                            DownloadState.DOWNLOADING -> DropdownMenuItem(
                                text = {
                                    Text(
                                        if (downloadProgress != null) "Downloading… ${(downloadProgress * 100).toInt()}%"
                                        else "Downloading…"
                                    )
                                },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                enabled = false,
                                onClick = {}
                            )
                            DownloadState.DOWNLOADED -> DropdownMenuItem(
                                text = { Text("Remove download") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = { showMenu = false; onDeleteDownload() }
                            )
                            DownloadState.FAILED -> DropdownMenuItem(
                                text = { Text("Retry download") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = { showMenu = false; onDownload() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Mark as played") },
                            leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                            enabled = !item.episode.isFinished,
                            onClick = { showMenu = false; onMarkAsPlayed() }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from queue") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onRemove()
                            }
                        )
                    }
                }
            }
        }
    }
    }
}
