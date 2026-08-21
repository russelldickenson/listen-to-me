package com.listentome.app.network

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"

class RssParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object RssParser {

    fun parse(input: InputStream): ParsedFeed {
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(input, null)

            var feedTitle = ""
            var feedDescription = ""
            var feedImageUrl: String? = null
            val items = mutableListOf<ParsedItem>()

            var eventType = parser.eventType
            var inChannelOnly = true
            var inImage = false
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (parser.namespace == ITUNES_NS && parser.name == "image" && inChannelOnly) {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) feedImageUrl = href
                    }
                    when (parser.name) {
                        "item" -> {
                            inChannelOnly = false
                            items += parseItem(parser)
                        }
                        "image" -> if (inChannelOnly && parser.namespace != ITUNES_NS) inImage = true
                        "title" -> if (inChannelOnly && !inImage) feedTitle = readText(parser)
                        "description" -> if (inChannelOnly) feedDescription = readText(parser)
                        "url" -> if (inChannelOnly && inImage && feedImageUrl == null) {
                            feedImageUrl = readText(parser)
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG && parser.name == "image") {
                    inImage = false
                }
                eventType = parser.next()
            }

            if (feedTitle.isBlank() && items.isEmpty()) {
                throw RssParseException("Feed did not contain a title or any episodes")
            }

            return ParsedFeed(
                title = feedTitle.ifBlank { "Untitled Podcast" },
                description = feedDescription,
                imageUrl = feedImageUrl,
                items = items
            )
        } catch (e: RssParseException) {
            throw e
        } catch (e: Exception) {
            throw RssParseException("Could not parse RSS feed: ${e.message}", e)
        }
    }

    private fun parseItem(parser: XmlPullParser): ParsedItem {
        var title = ""
        var description = ""
        var audioUrl: String? = null
        var guid: String? = null
        var pubDate: Long = 0
        var durationSeconds: Long? = null
        var imageUrl: String? = null

        val depth = parser.depth
        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG) {
                if (parser.namespace == ITUNES_NS && parser.name == "image") {
                    val href = parser.getAttributeValue(null, "href")
                    if (href != null) imageUrl = href
                }
                when (parser.name) {
                    "title" -> title = readText(parser)
                    "description" -> description = readText(parser)
                    "guid" -> guid = readText(parser)
                    "pubDate" -> pubDate = parseRfc822Date(readText(parser))
                    "enclosure" -> audioUrl = parser.getAttributeValue(null, "url")
                    "duration" -> if (parser.namespace == ITUNES_NS) {
                        durationSeconds = parseItunesDuration(readText(parser))
                    }
                }
            }
            eventType = parser.next()
        }

        val resolvedAudioUrl = audioUrl ?: ""
        return ParsedItem(
            guid = guid ?: resolvedAudioUrl.ifBlank { title },
            title = title.ifBlank { "Untitled Episode" },
            description = description,
            audioUrl = resolvedAudioUrl,
            publishedAt = pubDate,
            durationSeconds = durationSeconds,
            imageUrl = imageUrl
        )
    }

    private fun readText(parser: XmlPullParser): String {
        return if (parser.next() == XmlPullParser.TEXT) {
            parser.text.trim()
        } else {
            ""
        }
    }

    private fun parseItunesDuration(raw: String): Long? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0]
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> null
        }
    }

    private val dateFormatters = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss Z", Locale.US)
    )

    private fun parseRfc822Date(raw: String): Long {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return 0
        for (formatter in dateFormatters) {
            try {
                return ZonedDateTime.parse(trimmed, formatter).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // try next formatter
            }
        }
        return 0
    }
}
