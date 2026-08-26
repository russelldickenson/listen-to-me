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
                        "title" -> if (inChannelOnly && !inImage) feedTitle = readElementText(parser)
                        "description" -> if (inChannelOnly) feedDescription = readElementText(parser)
                        "url" -> if (inChannelOnly && inImage && feedImageUrl == null) {
                            feedImageUrl = readElementText(parser)
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
        var contentEncoded = ""
        var itunesSummary = ""
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
                    "title" -> title = readElementText(parser)
                    "description" -> description = readElementText(parser)
                    "encoded" -> contentEncoded = readElementText(parser)
                    "summary" -> if (parser.namespace == ITUNES_NS || itunesSummary.isEmpty()) {
                        itunesSummary = readElementText(parser)
                    }
                    "guid" -> guid = readElementText(parser)
                    "pubDate" -> pubDate = parseRfc822Date(readElementText(parser))
                    "enclosure" -> audioUrl = parser.getAttributeValue(null, "url")
                    "duration" -> if (parser.namespace == ITUNES_NS) {
                        durationSeconds = parseItunesDuration(readElementText(parser))
                    }
                }
            }
            eventType = parser.next()
        }

        val resolvedAudioUrl = audioUrl ?: ""
        val resolvedDescription = contentEncoded.ifBlank { description.ifBlank { itunesSummary } }
        return ParsedItem(
            guid = guid ?: resolvedAudioUrl.ifBlank { title },
            title = title.ifBlank { "Untitled Episode" },
            description = resolvedDescription,
            audioUrl = resolvedAudioUrl,
            publishedAt = pubDate,
            durationSeconds = durationSeconds,
            imageUrl = imageUrl
        )
    }

    private fun readElementText(parser: XmlPullParser): String {
        val result = StringBuilder()
        val targetDepth = parser.depth
        while (true) {
            val token = parser.next()
            if (token == XmlPullParser.END_DOCUMENT) break
            if (token == XmlPullParser.END_TAG && parser.depth == targetDepth) break
            when (token) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> result.append(parser.text)
                XmlPullParser.ENTITY_REF -> result.append(parser.text)
                XmlPullParser.START_TAG -> {
                    result.append("<").append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        result.append(" ")
                            .append(parser.getAttributeName(i))
                            .append("=\"")
                            .append(parser.getAttributeValue(i))
                            .append("\"")
                    }
                    result.append(">")
                }
                XmlPullParser.END_TAG -> {
                    result.append("</").append(parser.name).append(">")
                }
            }
        }
        return result.toString().trim()
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
