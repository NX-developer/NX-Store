package com.nxteam.nxstore.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

object HtmlText {

    private val bulletPrefix = Regex("^\\s*[*\\-•]\\s+")

    fun toAnnotated(raw: String): AnnotatedString {
        if (raw.isBlank()) return AnnotatedString("")
        val body = Jsoup.parseBodyFragment(normalize(raw)).body()
        return buildAnnotatedString {
            renderChildren(body, this, bold = false, italic = false)
        }.let { AnnotatedString(it.text.trim(), it.spanStyles, it.paragraphStyles) }
    }

    fun toPlain(raw: String): String = toAnnotated(raw).text

    private fun normalize(raw: String): String =
        raw.lineSequence()
            .joinToString("\n") { line -> line.replace(bulletPrefix, "• ") }

    private fun renderChildren(
        node: Node,
        builder: androidx.compose.ui.text.AnnotatedString.Builder,
        bold: Boolean,
        italic: Boolean
    ) {
        for (child in node.childNodes()) {
            when (child) {
                is TextNode -> {
                    val text = child.wholeText.replace('\u00A0', ' ')
                    if (text.isBlank() && !text.contains('\n')) {
                        if (text.isNotEmpty()) builder.append(" ")
                        continue
                    }
                    appendStyled(builder, text, bold, italic)
                }

                is Element -> when (child.tagName().lowercase()) {
                    "br" -> builder.append("\n")
                    "b", "strong" -> renderChildren(child, builder, true, italic)
                    "i", "em" -> renderChildren(child, builder, bold, true)
                    "p", "div" -> {
                        renderChildren(child, builder, bold, italic)
                        builder.append("\n\n")
                    }
                    "ul", "ol" -> {
                        builder.append("\n")
                        renderChildren(child, builder, bold, italic)
                    }
                    "li" -> {
                        builder.append("• ")
                        renderChildren(child, builder, bold, italic)
                        builder.append("\n")
                    }
                    "h1", "h2", "h3", "h4", "h5", "h6" -> {
                        builder.append("\n")
                        renderChildren(child, builder, true, italic)
                        builder.append("\n")
                    }
                    "script", "style" -> Unit
                    else -> renderChildren(child, builder, bold, italic)
                }
            }
        }
    }

    private fun appendStyled(
        builder: androidx.compose.ui.text.AnnotatedString.Builder,
        text: String,
        bold: Boolean,
        italic: Boolean
    ) {
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null
        )
        if (bold || italic) {
            builder.withStyle(style) { append(text) }
        } else {
            builder.append(text)
        }
    }
}
