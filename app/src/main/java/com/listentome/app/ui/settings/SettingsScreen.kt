package com.listentome.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.PlaylistAdd
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
        }
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
