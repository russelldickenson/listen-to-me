package com.listentome.app.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DownloadState {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = Feed::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedId"), Index(value = ["feedId", "guid"], unique = true)]
)
data class Episode(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedId: Long,
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishedAt: Long,
    val durationSeconds: Long?,
    val imageUrl: String? = null,
    val downloadState: DownloadState = DownloadState.NOT_DOWNLOADED,
    val localFilePath: String? = null,
    val playbackPositionMs: Long = 0,
    val isFinished: Boolean = false,
    val queuePosition: Long? = null,
    val manualSortOrder: Long? = null
)
