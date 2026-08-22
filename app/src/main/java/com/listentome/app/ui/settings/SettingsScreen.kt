package com.listentome.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import com.listentome.app.repository.OpmlImportResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val message by viewModel.message.collectAsState()
    val totalDownloadBytes by viewModel.totalDownloadBytes.collectAsState()
    val autoplayQueueEnabled by viewModel.autoplayQueueEnabled.collectAsState()
    val skipForwardSeconds by viewModel.skipForwardSeconds.collectAsState()
    val skipBackSeconds by viewModel.skipBackSeconds.collectAsState()
    val autoSkipBackOnResume by viewModel.autoSkipBackOnResume.collectAsState()

    var editingSkipForward by remember { mutableStateOf(false) }
    var editingSkipBack by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-opml")
    ) { uri -> uri?.let(viewModel::exportOpml) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importOpml) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding)) {
            ListItem(
                headlineContent = { Text("Export subscriptions") },
                supportingContent = { Text("Save your podcasts as an OPML file") },
                leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { exportLauncher.launch("listentome-subscriptions.opml") }
            )
            ListItem(
                headlineContent = { Text("Import subscriptions") },
                supportingContent = { Text("Add podcasts from an OPML file") },
                leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        importLauncher.launch(arrayOf("text/x-opml", "text/xml", "application/xml", "*/*"))
                    }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader("Downloads")

            ListItem(
                headlineContent = { Text("Downloaded episodes") },
                supportingContent = { Text(formatBytes(totalDownloadBytes)) },
                leadingContent = { Icon(Icons.Default.Download, contentDescription = null) },
                trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenDownloads)
            )
            ListItem(
                headlineContent = { Text("Autoplay queue") },
                supportingContent = { Text("Add downloaded episodes to queue") },
                leadingContent = { Icon(Icons.Default.PlaylistAdd, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = autoplayQueueEnabled,
                        onCheckedChange = viewModel::setAutoplayQueueEnabled
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutoplayQueueEnabled(!autoplayQueueEnabled) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionHeader("Player")

            ListItem(
                headlineContent = { Text("Skip forward time") },
                supportingContent = { Text("$skipForwardSeconds seconds") },
                leadingContent = { Icon(Icons.Default.FastForward, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingSkipForward = true }
            )
            ListItem(
                headlineContent = { Text("Skip back time") },
                supportingContent = { Text("$skipBackSeconds seconds") },
                leadingContent = { Icon(Icons.Default.FastRewind, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editingSkipBack = true }
            )
            ListItem(
                headlineContent = { Text("Auto Skip Back on Resume") },
                supportingContent = { Text("Resuming play skips back 3 seconds") },
                leadingContent = { Icon(Icons.Default.PlayCircle, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = autoSkipBackOnResume,
                        onCheckedChange = viewModel::setAutoSkipBackOnResume
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setAutoSkipBackOnResume(!autoSkipBackOnResume) }
            )
        }
    }

    if (editingSkipForward) {
        SkipTimeDialog(
            title = "Skip forward time",
            currentSeconds = skipForwardSeconds,
            onDismiss = { editingSkipForward = false },
            onSave = { seconds ->
                viewModel.setSkipForwardSeconds(seconds)
                editingSkipForward = false
            }
        )
    }

    if (editingSkipBack) {
        SkipTimeDialog(
            title = "Skip back time",
            currentSeconds = skipBackSeconds,
            onDismiss = { editingSkipBack = false },
            onSave = { seconds ->
                viewModel.setSkipBackSeconds(seconds)
                editingSkipBack = false
            }
        )
    }

    val currentMessage = message
    if (currentMessage != null) {
        val text = when (currentMessage) {
            is SettingsMessage.ExportFailed -> "Export failed: ${currentMessage.reason}"
            is SettingsMessage.ImportFailed -> "Import failed: ${currentMessage.reason}"
            is SettingsMessage.ImportComplete -> importSummary(currentMessage.result)
        }
        AlertDialog(
            onDismissRequest = viewModel::clearMessage,
            title = { Text(if (currentMessage is SettingsMessage.ImportComplete) "Import complete" else "Something went wrong") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = viewModel::clearMessage) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun SkipTimeDialog(
    title: String,
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var seconds by remember { mutableStateOf(currentSeconds) }
    val step = 5
    val minSeconds = 5
    val maxSeconds = 120

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { seconds = (seconds - step).coerceAtLeast(minSeconds) },
                    enabled = seconds > minSeconds
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease")
                }
                Text(
                    "$seconds seconds",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                IconButton(
                    onClick = { seconds = (seconds + step).coerceAtMost(maxSeconds) },
                    enabled = seconds < maxSeconds
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(seconds) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
    )
}

private fun importSummary(result: OpmlImportResult): String {
    val parts = mutableListOf("${result.added} added")
    if (result.skipped > 0) parts += "${result.skipped} already subscribed"
    if (result.failed > 0) parts += "${result.failed} failed"
    return parts.joinToString(", ")
}
