package com.listentome.app.ui.feedlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.listentome.app.data.Feed
import com.listentome.app.ui.components.HtmlText
import kotlin.math.roundToInt

private val FeedRowHeight = 88.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(
    viewModel: FeedListViewModel,
    onAddFeed: () -> Unit,
    onOpenFeed: (Long) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val feeds by viewModel.feeds.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val hasStaleFeeds by viewModel.hasStaleFeeds.collectAsState()

    var items by remember { mutableStateOf(feeds) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { FeedRowHeight.toPx() }

    LaunchedEffect(feeds) {
        if (draggedIndex == null) items = feeds
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Podcasts") },
                actions = {
                    IconButton(onClick = onOpenQueue) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                    }
                    IconButton(onClick = viewModel::refreshAll, enabled = !isRefreshing) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh all feeds")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddFeed) {
                Icon(Icons.Default.Add, contentDescription = "Add feed")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (hasStaleFeeds) {
                StaleDataBanner(isRefreshing = isRefreshing, onRefresh = viewModel::refreshAll)
            }
            if (items.isEmpty()) {
                EmptyState(onAddFeed = onAddFeed)
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    itemsIndexed(items, key = { _, feed -> feed.id }) { index, feed ->
                        val isDragging = index == draggedIndex
                        FeedRow(
                            feed = feed,
                            onClick = { onOpenFeed(feed.id) },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f },
                            dragHandleModifier = Modifier.pointerInput(feed.id) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        draggedIndex = items.indexOfFirst { it.id == feed.id }
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                        viewModel.reorderFeeds(items.map { it.id })
                                    },
                                    onDragCancel = {
                                        draggedIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount
                                        val from = draggedIndex ?: return@detectVerticalDragGestures
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
}

@Composable
private fun StaleDataBanner(isRefreshing: Boolean, onRefresh: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isRefreshing) "Refreshing…" else "Local episodes may be out of date.",
                modifier = Modifier.weight(1f)
            )
            Button(onClick = onRefresh, enabled = !isRefreshing) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun EmptyState(onAddFeed: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("No podcasts yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Add a podcast by its RSS feed URL to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(onClick = onAddFeed, modifier = Modifier.padding(top = 16.dp)) {
            Text("Add a feed")
        }
    }
}

@Composable
private fun FeedRow(
    feed: Feed,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth().height(FeedRowHeight).padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                modifier = dragHandleModifier.padding(end = 8.dp)
            )
            Row(
                modifier = Modifier.weight(1f).clickable(onClick = onClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = feed.imageUrl,
                    contentDescription = feed.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(feed.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    HtmlText(
                        html = feed.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
