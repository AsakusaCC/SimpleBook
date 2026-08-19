package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ByteBudgetLruCacheTest {

    private fun cache(maxBytes: Long) =
        ByteBudgetLruCache<String, String>(maxBytes) { it.length.toLong() }

    @Test
    fun putThenGetReturnsValue() {
        val c = cache(100)
        c.put("a", "aaaaa")
        assertEquals("aaaaa", c.get("a"))
        assertEquals(5L, c.bytes())
    }

    @Test
    fun exceedingBudgetEvictsEldest() {
        val c = cache(10)
        c.put("a", "aaaaaaaaaa") // 10 bytes — 正好占满
        c.put("b", "bbbbb")      // 5 bytes — 超预算，淘汰 a
        assertNull(c.get("a"))
        assertEquals("bbbbb", c.get("b"))
    }

    @Test
    fun getRefreshesRecency() {
        val c = cache(10)
        c.put("a", "aaaaa")
        c.put("b", "bbbbb") // a, b 各 5，正好满
        c.get("a")          // a 变为最近访问
        c.put("c", "ccccc") // 超预算 → 淘汰最久未访问的 b
        assertEquals("aaaaa", c.get("a"))
        assertNull(c.get("b"))
        assertEquals("ccccc", c.get("c"))
    }

    @Test
    fun putSameKeyReplacesValueAndBytes() {
        val c = cache(100)
        c.put("a", "aaa")
        c.put("a", "aaaaaaa")
        assertEquals(1, c.size())
        assertEquals(7L, c.bytes())
        assertEquals("aaaaaaa", c.get("a"))
    }

    @Test
    fun oversizedValueIsEvictedImmediately() {
        // 单个值超过总预算：放入即淘汰（缓存存不住，但不崩溃）
        val c = cache(4)
        c.put("big", "toolarge")
        assertNull(c.get("big"))
        assertEquals(0L, c.bytes())
    }

    @Test
    fun clearResetsEverything() {
        val c = cache(100)
        c.put("a", "aa")
        c.clear()
        assertNull(c.get("a"))
        assertEquals(0, c.size())
        assertEquals(0L, c.bytes())
    }

    @Test
    fun pdfPageCacheKeysOnBookPageAndBucket() {
        val c = PdfPageCache(maxBytes = 1_000_000)
        val bmp1 = ImageBitmap(10, 10)
        c.put("book1", 0, 800, bmp1)
        assertEquals(bmp1, c.get("book1", 0, 800))
        // 不同书 / 不同页 / 不同宽度桶都是 miss
        assertNull(c.get("book2", 0, 800))
        assertNull(c.get("book1", 1, 800))
        assertNull(c.get("book1", 0, 928))
    }
}

class PdfPageLoaderTest {

    private class FakePdfDocument(
        override val pageCount: Int = 3,
        private val failAfterClose: Boolean = true
    ) : PdfDocument {
        val rendered = mutableListOf<Pair<Int, Int>>()
        var closed = false

        override fun pageSizePts(index: Int): Size = Size(595f, 842f)

        override fun renderPage(index: Int, widthPx: Int): ImageBitmap {
            if (failAfterClose && closed) throw IllegalStateException("document closed")
            rendered += index to widthPx
            return ImageBitmap(widthPx, 10)
        }

        override fun close() { closed = true }
    }

    private fun loader(doc: FakePdfDocument) =
        PdfPageLoader(doc, PdfPageCache(maxBytes = 10_000_000), "book-1")

    @Test
    fun loadRendersOnceThenCacheHits() {
        val doc = FakePdfDocument()
        val l = loader(doc)
        kotlinx.coroutines.runBlocking {
            val b1 = l.load(0, 800)
            val b2 = l.load(0, 810) // 810 与 800 落在同一 128px 桶 → 缓存命中
            assertEquals(b1, b2)
        }
        assertEquals(1, doc.rendered.size)
    }

    @Test
    fun differentWidthBucketRendersAgain() {
        val doc = FakePdfDocument()
        val l = loader(doc)
        kotlinx.coroutines.runBlocking {
            l.load(0, 800)
            l.load(0, 928) // 不同桶 → 重新渲染
        }
        assertEquals(2, doc.rendered.size)
        assertEquals(896, doc.rendered[0].second) // 800 → 桶化到 896（128 的倍数 ≥ 800）
    }

    @Test
    fun outOfRangePageReturnsNull() {
        val l = loader(FakePdfDocument(pageCount = 3))
        kotlinx.coroutines.runBlocking {
            assertNull(l.load(5, 800))
            assertNull(l.load(-1, 800))
        }
    }

    @Test
    fun loadAfterCloseReturnsNull() {
        val doc = FakePdfDocument()
        val l = loader(doc)
        kotlinx.coroutines.runBlocking {
            assertNotNull(l.load(0, 800))
            l.close()
            assertNull(l.load(1, 800))
        }
    }

    @Test
    fun aspectRatioFallsBackOnBadDocument() {
        val doc = object : PdfDocument {
            override val pageCount = 1
            override fun pageSizePts(index: Int): Size = throw IllegalStateException("broken")
            override fun renderPage(index: Int, widthPx: Int): ImageBitmap = ImageBitmap(10, 10)
            override fun close() {}
        }
        val l = PdfPageLoader(doc, PdfPageCache(), "b")
        assertEquals(842f / 595f, l.aspectRatio(0), 0.001f)
    }

    @Test
    fun overallPercentageUsesPageMidpoint() {
        assertEquals(0.5 / 10.0, pdfOverallPercentage(0, 10), 1e-9)
        assertEquals(9.5 / 10.0, pdfOverallPercentage(9, 10), 1e-9)
        assertEquals(0.0, pdfOverallPercentage(0, 0))
    }

    @Test
    fun coercePageClampsToValidRange() {
        assertEquals(0, coercePdfPage(-3, 10))
        assertEquals(9, coercePdfPage(99, 10))
        assertEquals(5, coercePdfPage(5, 10))
        assertEquals(0, coercePdfPage(5, 0))
    }
}
