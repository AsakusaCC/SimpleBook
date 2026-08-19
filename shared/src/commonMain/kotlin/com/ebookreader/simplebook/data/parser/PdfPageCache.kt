package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 字节预算 LRU 缓存。访问序淘汰；超出预算从最久未访问项开始逐出。
 * 超过整个预算的单个值会在放入时立即被逐出（调用方拿到的值不受影响，只是缓存不命中）。
 */
internal class ByteBudgetLruCache<K, V>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Long
) {
    private val map = LinkedHashMap<K, V>(16, 0.75f, true)
    private var currentBytes = 0L

    @Synchronized
    fun get(key: K): V? = map[key]

    @Synchronized
    fun put(key: K, value: V) {
        map.put(key, value)?.let { old -> currentBytes -= sizeOf(old) }
        currentBytes += sizeOf(value)
        trim()
    }

    @Synchronized
    fun clear() {
        map.clear()
        currentBytes = 0
    }

    @Synchronized
    fun size(): Int = map.size

    @Synchronized
    fun bytes(): Long = currentBytes

    private fun trim() {
        val it = map.entries.iterator()
        while (currentBytes > maxBytes && it.hasNext()) {
            val entry = it.next()
            currentBytes -= sizeOf(entry.value)
            it.remove()
        }
    }
}

/** PDF 页位图缓存，key = (书, 页, 宽度桶)，字节口径 = 像素数 × 4（ARGB_8888）。 */
class PdfPageCache(maxBytes: Long = DEFAULT_MAX_BYTES) {
    private val cache = ByteBudgetLruCache<CacheKey, ImageBitmap>(maxBytes) {
        it.width.toLong() * it.height * 4L
    }

    fun get(bookUuid: String, page: Int, widthBucket: Int): ImageBitmap? =
        cache.get(CacheKey(bookUuid, page, widthBucket))

    fun put(bookUuid: String, page: Int, widthBucket: Int, bitmap: ImageBitmap) =
        cache.put(CacheKey(bookUuid, page, widthBucket), bitmap)

    fun clear() = cache.clear()

    private data class CacheKey(val bookUuid: String, val page: Int, val widthBucket: Int)

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 64L * 1024 * 1024
    }
}
