package com.listentome.app.network

data class ParsedFeed(
    val title: String,
    val description: String,
    val imageUrl: String?,
    val items: List<ParsedItem>
)

data class ParsedItem(
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val publishedAt: Long,
    val durationSeconds: Long?,
    val imageUrl: String?
)
