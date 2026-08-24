package com.listentome.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/**
 * Episode artwork with the download-failed/downloading state overlaid on the image, and the
 * downloaded/queued state shown as small icons underneath, tinted to match the row's text
 * color. Dims the artwork when the episode has been played.
 */
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
    val textColor = LocalContentColor.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(56.dp)
                    .alpha(if (isFinished) 0.5f else 1f)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
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
        }
        if (isQueued) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = "In queue",
                tint = textColor,
                modifier = Modifier.padding(top = 4.dp).size(36.dp)
            )
        }
    }
}

/** Small filled ring shown under the episode-actions button to indicate a completed download. */
@Composable
fun DownloadedIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        progress = { 1f },
        modifier = modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = LocalContentColor.current
    )
}

/**
 * Episode title, playback-progress bar, and published date, matching the per-podcast episode
 * list style. Pass [subtitle] (e.g. the podcast name) to show it alongside the date, for
 * contexts like the Queue where episodes from multiple podcasts are mixed together.
 */
@Composable
fun EpisodeInfoColumn(
    title: String,
    publishedAt: Long,
    playbackPositionMs: Long,
    durationSeconds: Long?,
    isFinished: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(lineHeight = 17.sp),
            color = if (isFinished) LocalContentColor.current.copy(alpha = 0.6f) else Color.Unspecified,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val durationMs = durationSeconds?.times(1000)
        if (!isFinished && playbackPositionMs > 0 && durationMs != null && durationMs > 0) {
            LinearProgressIndicator(
                progress = { (playbackPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
        Text(
            text = formatEpisodeDate(publishedAt),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatEpisodeDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(java.util.Date(epochMillis))
}
