package com.listentome.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.listentome.app.data.DownloadState
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Episode artwork with the download-failed/downloading state overlaid on the image, and the
 * downloaded/queued state shown as small icons underneath, tinted to match the row's text
 * color. Dims the artwork when the episode has been played. When [isCurrentEpisode] is true
 * (this is the episode loaded in the player), a translucent scrim and play/pause/buffering
 * glyph are overlaid on the artwork to mark it as the currently playing episode.
 */
@Composable
fun EpisodeArtwork(
    imageUrl: String?,
    contentDescription: String?,
    downloadState: DownloadState,
    downloadProgress: Float?,
    isFinished: Boolean,
    isQueued: Boolean,
    modifier: Modifier = Modifier,
    isCurrentEpisode: Boolean = false,
    isPlaying: Boolean = false,
    isBuffering: Boolean = false
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
            if (isCurrentEpisode) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Playing" else "Paused",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
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
        }
        if (isQueued) {
            Icon(
                Icons.Default.Menu,
                contentDescription = "In queue",
                tint = textColor,
                modifier = Modifier.padding(top = 4.dp).size(34.dp)
            )
        }
    }
}

/**
 * Ring that traces the episode-actions button to show download status: a light gray track
 * before a download starts, filling in with blue (and a leading dot) as it downloads, ending
 * as a full blue ring once downloaded.
 */
@Composable
fun DownloadStatusRing(downloadState: DownloadState, downloadProgress: Float?, modifier: Modifier = Modifier) {
    val trackColor = MaterialTheme.colorScheme.outlineVariant
    val progressColor = MaterialTheme.colorScheme.primary
    val progress = when (downloadState) {
        DownloadState.DOWNLOADED -> 1f
        DownloadState.DOWNLOADING -> (downloadProgress ?: 0f).coerceIn(0f, 1f)
        else -> 0f
    }
    Canvas(modifier = modifier.size(40.dp)) {
        val strokeWidthPx = 2.dp.toPx()
        val diameter = size.minDimension - strokeWidthPx
        val topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f)
        val arcSize = Size(diameter, diameter)
        drawArc(
            color = trackColor,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
        )
        if (progress > 0f) {
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
        }
        if (downloadState == DownloadState.DOWNLOADING && progress in 0f..1f) {
            val radius = diameter / 2f
            val angleRad = Math.toRadians((-90f + 360f * progress).toDouble())
            val center = Offset(size.width / 2f, size.height / 2f)
            val dotCenter = Offset(
                x = center.x + radius * cos(angleRad).toFloat(),
                y = center.y + radius * sin(angleRad).toFloat()
            )
            drawCircle(color = progressColor, radius = strokeWidthPx * 1.5f, center = dotCenter)
        }
    }
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
    isFinished: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp, lineHeight = 19.sp),
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
