package com.listentome.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class Feed(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val url: String,
    val title: String,
    val description: String,
    val imageUrl: String?,
    val lastRefreshedAt: Long?,
    val keepLatestCount: Int = 3,
    val sortOrder: Long = 0
)
