package io.grimoire.api.network

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

private val MARGIN_LEFT_PX = Regex("""margin-left\s*:\s*(\d+)\s*px""", RegexOption.IGNORE_CASE)
private val WHITESPACE_RUN = Regex("""\s+""")

/**
 * Serialises the children of this element into a constrained HTML subset
 * suitable for Compose's `AnnotatedString.fromHtml`. Sources opt in by
 * setting `NovelPage.formattedText = element.richHtml()` alongside the
 * existing plain-text `text`; the reader picks up the rich rendering and
 * TTS/search/etc. keep using `text`.
 *
 * Recognised input → output:
 *  - `<br>` → `<br>`
 *  - `<em>`, `<i>` → `<i>…</i>`
 *  - `<strong>`, `<b>` → `<b>…</b>`
 *  - `<u>` → `<u>…</u>`
 *  - `<a href>` → `<a href="…">…</a>` (absolutised when Jsoup has a base URI)
 *  - `<span style="margin-left: Npx">` → runs of `&nbsp;` (two per 15px, capped
 *    at eight indent levels) emitted immediately after the preceding `<br>`
 *  - Body text → HTML-escaped (`<`, `>`, `&`)
 *
 * Other elements are unwrapped (children processed in place); comments and
 * unknown void elements are dropped. The result is trimmed.
 */
fun Element.richHtml(): String {
    val out = StringBuilder()
    // Track explicit line-start state instead of inspecting the buffer tail:
    // opening tags like <i> or <a href> render nothing, but a tail check via
    // endsWith("<br>") would incorrectly conclude we'd left line-start when
    // wrapping an indented <span> in italics.
    var lineStart = true

    lateinit var walk: (Node) -> Unit

    fun wrap(tag: String, node: Element) {
        out.append('<').append(tag).append('>')
        node.childNodes().forEach(walk)
        out.append("</").append(tag).append('>')
    }

    walk = { node ->
        when (node) {
            is TextNode -> {
                val raw = node.wholeText
                if (raw.isNotEmpty()) {
                    val collapsed = raw.replace(WHITESPACE_RUN, " ")
                    val rendered = if (lineStart) collapsed.trimStart() else collapsed
                    if (rendered.isNotEmpty()) {
                        out.append(rendered.htmlEscape())
                        lineStart = false
                    }
                }
            }
            is Element -> when (node.tagName().lowercase()) {
                "br" -> {
                    out.append("<br>")
                    lineStart = true
                }
                "em", "i" -> wrap("i", node)
                "strong", "b" -> wrap("b", node)
                "u" -> wrap("u", node)
                "a" -> {
                    val href = node.attr("abs:href").ifEmpty { node.attr("href") }
                    if (href.isEmpty()) {
                        node.childNodes().forEach(walk)
                    } else {
                        out.append("<a href=\"").append(href.htmlEscape(quotes = true)).append("\">")
                        node.childNodes().forEach(walk)
                        out.append("</a>")
                    }
                }
                "span" -> {
                    val px = MARGIN_LEFT_PX.find(node.attr("style"))
                        ?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val nbspCount = (px / 15).coerceIn(0, 8) * 2
                    if (nbspCount > 0 && lineStart) {
                        repeat(nbspCount) { out.append("&nbsp;") }
                        lineStart = false
                    }
                    node.childNodes().forEach(walk)
                }
                else -> node.childNodes().forEach(walk)
            }
            else -> {}
        }
    }

    childNodes().forEach(walk)
    return out.toString().trim()
}

private fun String.htmlEscape(quotes: Boolean = false): String {
    val sb = StringBuilder(length)
    for (c in this) {
        when (c) {
            '&' -> sb.append("&amp;")
            '<' -> sb.append("&lt;")
            '>' -> sb.append("&gt;")
            '"' -> if (quotes) sb.append("&quot;") else sb.append(c)
            '\'' -> if (quotes) sb.append("&#39;") else sb.append(c)
            else -> sb.append(c)
        }
    }
    return sb.toString()
}
