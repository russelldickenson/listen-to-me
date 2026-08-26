package com.listentome.app.ui.components

import android.graphics.Typeface
import android.text.Spanned
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.text.HtmlCompat

/** Renders a string that may contain HTML markup (as found in RSS/iTunes descriptions) as styled text. */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val annotated = remember(html) { html.toAnnotatedString() }
    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        overflow = overflow
    )
}

private val TAG_ENTITY_REGEX = Regex("&lt;(/?[a-zA-Z][a-zA-Z0-9]*(?:\\s+[^&>]*?)?/?)(&gt;|>)")
private val BOLD_TAG_REGEX = Regex("<(?:b|strong)>(.*?)</(?:b|strong)>", RegexOption.IGNORE_CASE)

private fun String.toAnnotatedString(): AnnotatedString {
    val normalized = this.replace(TAG_ENTITY_REGEX, "<$1>")
    val spanned = HtmlCompat.fromHtml(normalized, HtmlCompat.FROM_HTML_MODE_COMPACT)
    return buildAnnotatedString(spanned)
}

private fun buildAnnotatedString(spanned: Spanned): AnnotatedString {
    val rawText = spanned.toString().trimEnd('\n')

    if (!rawText.contains("<b>", ignoreCase = true) && !rawText.contains("<strong>", ignoreCase = true)) {
        return AnnotatedString.Builder(rawText).apply {
            for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
                val start = spanned.getSpanStart(span).coerceIn(0, rawText.length)
                val end = spanned.getSpanEnd(span).coerceIn(0, rawText.length)
                if (start >= end) continue
                val spanStyle = when (span) {
                    is StyleSpan -> when (span.style) {
                        Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                        Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                        Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                        else -> null
                    }
                    is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                    is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    is URLSpan -> SpanStyle(
                        color = Color(0xFF90CAF9),
                        textDecoration = TextDecoration.Underline
                    )
                    else -> null
                }
                if (spanStyle != null) {
                    addStyle(spanStyle, start, end)
                }
            }
        }.toAnnotatedString()
    }

    val cleanBuilder = StringBuilder()
    val boldRanges = mutableListOf<IntRange>()
    var lastIndex = 0
    for (match in BOLD_TAG_REGEX.findAll(rawText)) {
        cleanBuilder.append(rawText.substring(lastIndex, match.range.first))
        val start = cleanBuilder.length
        val innerText = match.groupValues[1]
        cleanBuilder.append(innerText)
        val end = cleanBuilder.length
        boldRanges.add(start until end)
        lastIndex = match.range.last + 1
    }
    cleanBuilder.append(rawText.substring(lastIndex))
    val finalText = cleanBuilder.toString()

    return AnnotatedString.Builder(finalText).apply {
        for (span in spanned.getSpans(0, spanned.length, Any::class.java)) {
            val start = spanned.getSpanStart(span).coerceIn(0, finalText.length)
            val end = spanned.getSpanEnd(span).coerceIn(0, finalText.length)
            if (start >= end) continue
            val spanStyle = when (span) {
                is StyleSpan -> when (span.style) {
                    Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                    Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                    Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    else -> null
                }
                is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                is URLSpan -> SpanStyle(
                    color = Color(0xFF90CAF9),
                    textDecoration = TextDecoration.Underline
                )
                else -> null
            }
            if (spanStyle != null) {
                addStyle(spanStyle, start, end)
            }
        }
        for (range in boldRanges) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), range.first, range.last + 1)
        }
    }.toAnnotatedString()
}
