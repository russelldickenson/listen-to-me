package com.listentome.app.ui.feedlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.listentome.app.ui.components.ReorderHintBanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.listentome.app.R
import com.listentome.app.data.Feed
import com.listentome.app.ui.components.HtmlText
import kotlin.math.roundToInt

private val FeedRowHeight = 88.dp

private fun formatRelativeRefreshTime(epochMillis: Long): String {
    val diffMs = (System.currentTimeMillis() - epochMillis).coerceAtLeast(0)
    val minutes = diffMs / 60_000
    val hours = diffMs / 3_600_000
    val days = diffMs / 86_400_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        hours < 24 -> "$hours hour${if (hours == 1L) "" else "s"} ago"
        else -> "$days day${if (days == 1L) "" else "s"} ago"
    }
}

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
    val refreshProgress by viewModel.refreshProgress.collectAsState()
    val hasStaleFeeds by viewModel.hasStaleFeeds.collectAsState()
    val hasEpisodes by viewModel.hasEpisodes.collectAsState()
    val lastRefreshedAt by viewModel.lastRefreshedAt.collectAsState()
    val reorderHintDismissed by viewModel.reorderHintDismissed.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    var items by remember { mutableStateOf(feeds) }
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { FeedRowHeight.toPx() }

    LaunchedEffect(feeds) {
        if (draggedIndex == null) items = feeds
    }

    LaunchedEffect(viewModel) {
        viewModel.errorMessages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(hasStaleFeeds) {
        if (hasStaleFeeds) {
            val result = snackbarHostState.showSnackbar(
                message = "Episodes might be out of date",
                actionLabel = "Refresh",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.refreshAll()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Text("Listen To Me", modifier = Modifier.padding(start = 8.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenQueue, enabled = hasEpisodes) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                    }
                    IconButton(onClick = viewModel::refreshAll, enabled = !isRefreshing && refreshProgress == null && hasEpisodes) {
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { if (hasEpisodes) viewModel.refreshAll() },
                modifier = Modifier.fillMaxSize(),
                indicator = {}
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    val refreshedAt = lastRefreshedAt
                    if (isRefreshing || refreshProgress != null) {
                        RefreshingRow(refreshProgress = refreshProgress)
                    } else if (refreshedAt != null) {
                        Text(
                            text = "Refreshed ${formatRelativeRefreshTime(refreshedAt)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)
                        )
                    }
                    if (!reorderHintDismissed && items.size >= 2) {
                        ReorderHintBanner(
                            onDismiss = viewModel::dismissReorderHint,
                            text = "Tip: Long-press and drag any podcast to reorder your list."
                        )
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
                                    isDragging = isDragging,
                                    modifier = Modifier
                                        .zIndex(if (isDragging) 1f else 0f)
                                        .graphicsLayer { translationY = if (isDragging) dragOffsetY else 0f }
                                        .pointerInput(feed.id) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

@Composable
private fun RefreshingRow(refreshProgress: Pair<Int, Int>?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(
                text = "Refreshing...",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        if (refreshProgress != null) {
            val (completed, total) = refreshProgress
            Text(
                text = "$completed/$total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp).clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 8.dp else 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                CardDefaults.cardColors().containerColor
            }
        )
    ) {
        var expanded by remember { mutableStateOf(false) }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(20.dp)
                    .semantics { contentDescription = "Reorder" },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight(0.75f)
                        .width(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
            }
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    AsyncImage(
                        model = feed.imageUrl,
                        contentDescription = feed.title,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Row(
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            feed.title,
                            style = MaterialTheme.typography.titleMedium.copy(lineHeight = 20.sp),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = if (expanded) "Hide description" else "Show description",
                                tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (expanded) {
                    HtmlText(
                        html = feed.description,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
