package com.listentome.app.opml

import android.util.Xml
import com.listentome.app.data.Feed
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringWriter

data class OpmlOutline(val title: String, val xmlUrl: String)

object OpmlWriter {
    fun write(feeds: List<Feed>): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.startTag(null, "opml")
        serializer.attribute(null, "version", "2.0")

        serializer.startTag(null, "head")
        serializer.startTag(null, "title")
        serializer.text("ListenToMe Subscriptions")
        serializer.endTag(null, "title")
        serializer.endTag(null, "head")

        serializer.startTag(null, "body")
        feeds.forEach { feed ->
            serializer.startTag(null, "outline")
            serializer.attribute(null, "text", feed.title)
            serializer.attribute(null, "title", feed.title)
            serializer.attribute(null, "type", "rss")
            serializer.attribute(null, "xmlUrl", feed.url)
            serializer.endTag(null, "outline")
        }
        serializer.endTag(null, "body")

        serializer.endTag(null, "opml")
        serializer.endDocument()
        return writer.toString()
    }
}

object OpmlParser {
    fun parse(input: InputStream): List<OpmlOutline> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        val outlines = mutableListOf<OpmlOutline>()
        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "outline") {
                val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                if (!xmlUrl.isNullOrBlank()) {
                    val title = parser.getAttributeValue(null, "title")
                        ?: parser.getAttributeValue(null, "text")
                        ?: xmlUrl
                    outlines += OpmlOutline(title = title, xmlUrl = xmlUrl)
                }
            }
            eventType = parser.next()
        }
        return outlines
    }
}
