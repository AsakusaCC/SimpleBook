package com.ebookreader.simplebook.ui.reader

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 纯函数：把 EPUB 章节 HTML 解析为 [HtmlBlock] 列表。
 * jsoup 宽松解析，不规范的 HTML 也不抛；外层再 try-catch 兜底。
 */
object HtmlBlockParser {

    fun parse(html: String): List<HtmlBlock> {
        if (html.isBlank()) return emptyList()
        return try {
            val doc = Jsoup.parse(html)
            val blocks = mutableListOf<HtmlBlock>()
            for (node in doc.body().childNodes()) {
                collectBlocks(node, blocks)
            }
            blocks.ifEmpty { fallbackParagraph(html) }
        } catch (_: Exception) {
            fallbackParagraph(html)
        }
    }

    private fun fallbackParagraph(html: String): List<HtmlBlock> {
        val text = html.replace(Regex("<[^>]+>"), "").trim()
        return if (text.isNotEmpty()) {
            listOf(HtmlBlock.Paragraph(InlineContent(listOf(InlineSpan(text)))))
        } else {
            emptyList()
        }
    }

    private fun collectBlocks(node: Node, out: MutableList<HtmlBlock>) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText.trim()
                if (text.isNotEmpty()) {
                    out.add(HtmlBlock.Paragraph(InlineContent(listOf(InlineSpan(text)))))
                }
            }
            is Element -> when (node.tagName().lowercase()) {
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val level = node.tagName().last().digitToInt()
                    val content = parseInline(node)
                    if (content.spans.isNotEmpty()) {
                        out.add(HtmlBlock.Heading(level, content))
                    }
                }
                "p" -> {
                    val content = parseInline(node)
                    if (content.spans.isNotEmpty()) {
                        out.add(HtmlBlock.Paragraph(content))
                    }
                }
                "img" -> out.add(
                    HtmlBlock.Image(
                        src = node.attr("src"),
                        alt = node.attr("alt").takeIf { it.isNotBlank() }
                    )
                )
                "blockquote" -> out.add(HtmlBlock.Quote(parseInline(node)))
                "hr" -> out.add(HtmlBlock.Rule)
                "pre" -> {
                    val text = node.text()
                    if (text.isNotEmpty()) out.add(HtmlBlock.CodeBlock(text))
                }
                "ul" -> {
                    node.childNodes()
                        .filterIsInstance<Element>()
                        .forEachIndexed { idx, li ->
                            if (li.tagName().lowercase() == "li") {
                                out.add(HtmlBlock.ListItem(parseInline(li), ordered = false, index = idx + 1))
                            }
                        }
                }
                "ol" -> {
                    node.childNodes()
                        .filterIsInstance<Element>()
                        .forEachIndexed { idx, li ->
                            if (li.tagName().lowercase() == "li") {
                                out.add(HtmlBlock.ListItem(parseInline(li), ordered = true, index = idx + 1))
                            }
                        }
                }
                else -> {
                    // 容器（div/section/article 等）：递归处理子节点
                    for (child in node.childNodes()) {
                        collectBlocks(child, out)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun parseInline(element: Element): InlineContent {
        val spans = mutableListOf<InlineSpan>()
        collectInline(element, spans, bold = false, italic = false, code = false, href = null)
        return InlineContent(spans)
    }

    private fun collectInline(
        node: Node,
        out: MutableList<InlineSpan>,
        bold: Boolean,
        italic: Boolean,
        code: Boolean,
        href: String?
    ) {
        when (node) {
            is TextNode -> {
                val text = node.wholeText
                if (text.isNotEmpty()) {
                    out.add(InlineSpan(text, bold, italic, code, href))
                }
            }
            is Element -> {
                val tag = node.tagName().lowercase()
                val childHref = node.attr("href").takeIf { it.isNotBlank() } ?: href
                when (tag) {
                    "br" -> out.add(InlineSpan("\n"))
                    "b", "strong" ->
                        node.childNodes().forEach { collectInline(it, out, true, italic, code, childHref) }
                    "i", "em" ->
                        node.childNodes().forEach { collectInline(it, out, bold, true, code, childHref) }
                    "code" ->
                        node.childNodes().forEach { collectInline(it, out, bold, italic, true, childHref) }
                    "a" ->
                        node.childNodes().forEach { collectInline(it, out, bold, italic, code, childHref) }
                    else ->
                        node.childNodes().forEach { collectInline(it, out, bold, italic, code, childHref) }
                }
            }
            else -> Unit
        }
    }
}
