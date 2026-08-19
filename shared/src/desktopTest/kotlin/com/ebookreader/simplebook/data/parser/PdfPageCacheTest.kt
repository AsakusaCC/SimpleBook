package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
