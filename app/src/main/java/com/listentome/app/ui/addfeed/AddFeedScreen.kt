package com.listentome.app.ui.addfeed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.listentome.app.network.ParsedFeed
import com.listentome.app.ui.components.HtmlText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedScreen(
    viewModel: AddFeedViewModel,
    onBack: () -> Unit,
    onFeedAdded: (Long) -> Unit
) {
    var url by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AddFeedUiState.Success) {
            onFeedAdded(state.feedId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add podcast") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp)) {
            val state = uiState
            if (state is AddFeedUiState.Preview) {
                FeedPreview(
                    parsed = state.parsed,
                    onConfirm = viewModel::confirmAdd,
                    onCancel = viewModel::cancelPreview
                )
            } else if (state is AddFeedUiState.Loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 32.dp))
            } else {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RSS feed URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (state is AddFeedUiState.Error) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Button(
                    onClick = { viewModel.fetchPreview(url) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) {
                    Text("Preview")
                }
            }
        }
    }
}

@Composable
private fun FeedPreview(
    parsed: ParsedFeed,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = parsed.imageUrl,
                contentDescription = parsed.title,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(parsed.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${parsed.items.size} episode${if (parsed.items.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HtmlText(
            html = parsed.description,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text("Add podcast")
            }
        }
    }
}
