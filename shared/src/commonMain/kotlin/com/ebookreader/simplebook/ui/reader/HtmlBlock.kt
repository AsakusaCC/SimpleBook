package com.ebookreader.simplebook.ui.reader

/**
 * 行内文本片段（纯数据，无 Compose 依赖，便于纯 JVM 单测）。
 */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val href: String? = null
)

/**
 * 一个块级元素内的行内内容。
 */
data class InlineContent(val spans: List<InlineSpan> = emptyList())

/**
 * HTML 块级元素（EPUB 章节解析结果）。Parser 的输出、Renderer 的输入。
 */
sealed class HtmlBlock {
    data class Heading(val level: Int, val content: InlineContent) : HtmlBlock()
    data class Paragraph(val content: InlineContent) : HtmlBlock()
    data class Image(val src: String, val alt: String?) : HtmlBlock()
    data class Quote(val content: InlineContent) : HtmlBlock()
    data class ListItem(val content: InlineContent, val ordered: Boolean, val index: Int) : HtmlBlock()
    data class CodeBlock(val text: String) : HtmlBlock()
    data object Rule : HtmlBlock()
}
