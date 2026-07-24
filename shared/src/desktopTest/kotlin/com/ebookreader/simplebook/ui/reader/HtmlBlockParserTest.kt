package com.ebookreader.simplebook.ui.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlBlockParserTest {

    @Test
    fun parsesSimpleParagraph() {
        val blocks = HtmlBlockParser.parse("<p>Hello world</p>")
        assertEquals(1, blocks.size)
        val p = blocks[0]
        assertTrue(p is HtmlBlock.Paragraph)
        assertEquals("Hello world", p.content.spans.single().text)
    }

    @Test
    fun parsesHeadingsWithLevels() {
        val blocks = HtmlBlockParser.parse("<h1>Title1</h1><h3>Sub</h3>")
        assertEquals(2, blocks.size)
        val h1 = blocks[0] as HtmlBlock.Heading
        val h3 = blocks[1] as HtmlBlock.Heading
        assertEquals(1, h1.level)
        assertEquals(3, h3.level)
        assertEquals("Title1", h1.content.spans.single().text)
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertTrue(HtmlBlockParser.parse("").isEmpty())
        assertTrue(HtmlBlockParser.parse("   ").isEmpty())
    }

    @Test
    fun parsesInlineBoldAndItalic() {
        val blocks = HtmlBlockParser.parse("<p>plain <b>bold</b> <i>italic</i> tail</p>")
        val spans = (blocks[0] as HtmlBlock.Paragraph).content.spans
        assertTrue(spans.any { it.bold && it.text == "bold" })
        assertTrue(spans.any { it.italic && it.text == "italic" })
        assertTrue(spans.any { it.text.contains("plain") })
        assertTrue(spans.any { it.text.contains("tail") })
    }

    @Test
    fun parsesStrongAndEmAsBoldItalic() {
        val blocks = HtmlBlockParser.parse("<p><strong>s</strong><em>e</em></p>")
        val spans = (blocks[0] as HtmlBlock.Paragraph).content.spans
        assertTrue(spans.any { it.bold && it.text == "s" })
        assertTrue(spans.any { it.italic && it.text == "e" })
    }

    @Test
    fun parsesInlineCode() {
        val blocks = HtmlBlockParser.parse("<p>a <code>foo()</code> b</p>")
        val spans = (blocks[0] as HtmlBlock.Paragraph).content.spans
        assertTrue(spans.any { it.code && it.text == "foo()" })
    }

    @Test
    fun parsesLinkHref() {
        val blocks = HtmlBlockParser.parse("<p>see <a href=\"https://x.example\">link</a></p>")
        val spans = (blocks[0] as HtmlBlock.Paragraph).content.spans
        assertTrue(spans.any { it.href == "https://x.example" && it.text == "link" })
    }

    @Test
    fun decodesHtmlEntities() {
        val blocks = HtmlBlockParser.parse("<p>a &amp; b &lt; c</p>")
        val text = (blocks[0] as HtmlBlock.Paragraph).content.spans.joinToString("") { it.text }
        assertTrue(text.contains("a & b < c"))
    }

    @Test
    fun breaksLineOnBr() {
        val blocks = HtmlBlockParser.parse("<p>line1<br>line2</p>")
        val text = (blocks[0] as HtmlBlock.Paragraph).content.spans.joinToString("") { it.text }
        assertTrue(text.contains("line1"))
        assertTrue(text.contains("line2"))
        assertTrue(text.contains("\n"))
    }

    @Test
    fun parsesImageWithDataUri() {
        val src = "data:image/png;base64,iVBORw0KGgo="
        val blocks = HtmlBlockParser.parse("<p>x</p><img src=\"$src\" alt=\"cover\"/>")
        val img = blocks.filterIsInstance<HtmlBlock.Image>().single()
        assertEquals(src, img.src)
        assertEquals("cover", img.alt)
    }

    @Test
    fun parsesSvgWrappedImageXlinkHref() {
        // 真实样本写法：图片型 EPUB 每页一张 jpg，用 svg 包裹做尺寸适配
        val html = """
            <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
                 width="100%" height="100%" viewBox="0 0 867 1300">
                <image width="867" height="1300" xlink:href="../Images/200273.jpg"/>
            </svg>
        """.trimIndent()
        val blocks = HtmlBlockParser.parse(html)
        val img = blocks.filterIsInstance<HtmlBlock.Image>().single()
        assertEquals("../Images/200273.jpg", img.src)
    }

    @Test
    fun parsesSvgImageBareHref() {
        // SVG2 写法（裸 href，无 xlink: 前缀）
        val blocks = HtmlBlockParser.parse("<svg><image href=\"cover.png\"/></svg>")
        val img = blocks.filterIsInstance<HtmlBlock.Image>().single()
        assertEquals("cover.png", img.src)
    }

    @Test
    fun parsesUnorderedList() {
        val blocks = HtmlBlockParser.parse("<ul><li>a</li><li>b</li></ul>")
        val items = blocks.filterIsInstance<HtmlBlock.ListItem>()
        assertEquals(2, items.size)
        assertFalse(items.all { it.ordered })
        assertEquals(1, items[0].index)
        assertEquals("a", items[0].content.spans.single().text)
        assertEquals(2, items[1].index)
    }

    @Test
    fun parsesOrderedList() {
        val blocks = HtmlBlockParser.parse("<ol><li>one</li><li>two</li></ol>")
        val items = blocks.filterIsInstance<HtmlBlock.ListItem>()
        assertTrue(items.all { it.ordered })
        assertEquals(2, items.size)
    }

    @Test
    fun parsesBlockquote() {
        val blocks = HtmlBlockParser.parse("<blockquote>a quote</blockquote>")
        val q = blocks.filterIsInstance<HtmlBlock.Quote>().single()
        assertEquals("a quote", q.content.spans.single().text)
    }

    @Test
    fun parsesHorizontalRule() {
        val blocks = HtmlBlockParser.parse("<p>x</p><hr/><p>y</p>")
        assertTrue(blocks.any { it is HtmlBlock.Rule })
        assertEquals(3, blocks.size)
    }

    @Test
    fun parsesPreAsCodeBlock() {
        val blocks = HtmlBlockParser.parse("<pre>def f(): pass</pre>")
        val code = blocks.filterIsInstance<HtmlBlock.CodeBlock>().single()
        assertTrue(code.text.contains("def f"))
    }

    @Test
    fun recursesIntoContainerDiv() {
        val blocks = HtmlBlockParser.parse("<div><p>inside</p></div>")
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is HtmlBlock.Paragraph)
        assertEquals("inside", (blocks[0] as HtmlBlock.Paragraph).content.spans.single().text)
    }

    @Test
    fun ignoresUnknownTagsKeepsText() {
        val blocks = HtmlBlockParser.parse("<p>hello <span class=\"x\">world</span></p>")
        val text = (blocks[0] as HtmlBlock.Paragraph).content.spans.joinToString("") { it.text }
        assertTrue(text.contains("hello"))
        assertTrue(text.contains("world"))
    }

    @Test
    fun fallsBackToPlainTextOnUnparseableInput() {
        // 不崩溃即通过：纯文本或异常输入都应返回（可能空的）列表
        val blocks = HtmlBlockParser.parse("just plain text, no tags")
        assertTrue(blocks.isNotEmpty())
    }
}
