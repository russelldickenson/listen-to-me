package com.listentome.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.listentome.app.data.DownloadState
import java.text.SimpleDateFormat
import java.util.Locale

/** Episode artwork with download-state, played, and queued badges overlaid, matching the per-podcast episode list style. */
@Composable
fun EpisodeArtwork(
    imageUrl: String?,
    contentDescription: String?,
    downloadState: DownloadState,
    downloadProgress: Float?,
    isFinished: Boolean,
    isQueued: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        if (downloadState == DownloadState.DOWNLOADED) {
            Icon(
                Icons.Default.Download,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(3.dp)
            )
        }
        if (downloadState == DownloadState.FAILED) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Download failed",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 4.dp, y = 4.dp)
                    .size(18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .padding(3.dp)
            )
        }
        if (downloadState == DownloadState.DOWNLOADING) {
            if (downloadProgress != null) {
                CircularProgressIndicator(
                    progress = { downloadProgress },
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 3.dp,
                    color = Color.White,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(28.dp),
                    strokeWidth = 3.dp,
                    color = Color.White
                )
            }
        }
        if (isFinished || isQueued) {
            Row(
                modifier = Modifier.align(Alignment.BottomCenter).offset(y = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isFinished) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Played",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(3.dp)
                    )
                }
                if (isQueued) {
                    Icon(
                        Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "In queue",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(3.dp)
                    )
                }
            }
        }
    }
}

/** Episode title, playback-progress bar, and published date, matching the per-podcast episode list style. */
@Composable
fun EpisodeInfoColumn(
    title: String,
    publishedAt: Long,
    playbackPositionMs: Long,
    durationSeconds: Long?,
    isFinished: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 17.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val durationMs = durationSeconds?.times(1000)
        if (!isFinished && playbackPositionMs > 0 && durationMs != null && durationMs > 0) {
            LinearProgressIndicator(
                progress = { (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Text(formatEpisodeDate(publishedAt), style = MaterialTheme.typography.bodySmall)
    }
}

/** Play/pause control styled as a filled rounded-square button, matching the per-podcast episode list style. */
@Composable
fun EpisodePlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play"
        )
    }
}

fun formatEpisodeDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(epochMillis))
}
