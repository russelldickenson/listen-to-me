package com.listentome.app.ui.feedlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.listentome.app.data.Feed
import com.listentome.app.ui.components.HtmlText

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
            if (feeds.isEmpty()) {
                EmptyState(onAddFeed = onAddFeed)
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp)) {
                    items(feeds, key = { it.id }) { feed ->
                        FeedRow(feed = feed, onClick = { onOpenFeed(feed.id) })
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
private fun FeedRow(feed: Feed, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = feed.imageUrl,
                contentDescription = feed.title,
                modifier = Modifier.size(56.dp)
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
