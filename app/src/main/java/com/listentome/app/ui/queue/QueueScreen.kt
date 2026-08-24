package com.listentome.app.ui.queue

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.listentome.app.ui.components.EpisodeArtwork
import com.listentome.app.ui.components.EpisodeInfoColumn
import kotlin.math.roundToInt

private val QueueRowHeight = 108.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val queue by viewModel.queue.collectAsState()
    val playback by viewModel.playback.collectAsState()

    var items by remember { mutableStateOf(queue) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { QueueRowHeight.toPx() }

    LaunchedEffect(queue) {
        if (draggedIndex == null) items = queue
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        val isQueuePlaying = playback.isPlaying && playback.isQueuePlaylist &&
                            items.any { it.episode.id == playback.currentEpisodeId }
                        IconButton(onClick = viewModel::toggleQueuePlayback) {
                            Icon(
                                imageVector = if (isQueuePlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isQueuePlaying) "Pause queue" else "Play queue"
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your queue is empty. Long-press an episode and choose \"Add to queue\".")
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.padding(padding)) {
                itemsIndexed(items, key = { _, item -> item.episode.id }) { index, item ->
                    val isDragging = index == draggedIndex
                    QueueRow(
                        item = item,
                        isDragging = isDragging,
                        onPlay = { viewModel.playOrToggle(item, onOpenPlayer = onPlay) },
                        onRemove = { viewModel.removeFromQueue(item.episode) },
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                            .pointerInput(item.episode.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
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
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    isDragging: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().height(QueueRowHeight).padding(bottom = 12.dp).clickable(onClick = onPlay),
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
                imageUrl = item.episode.imageUrl ?: item.feed?.imageUrl,
                contentDescription = item.episode.title,
                downloadState = item.episode.downloadState,
                downloadProgress = null,
                isFinished = item.episode.isFinished,
                isQueued = item.episode.queuePosition != null
            )
            EpisodeInfoColumn(
                title = item.episode.title,
                publishedAt = item.episode.publishedAt,
                playbackPositionMs = item.episode.playbackPositionMs,
                durationSeconds = item.episode.durationSeconds,
                isFinished = item.episode.isFinished,
                subtitle = item.feed?.title,
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            )
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Episode actions")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
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
