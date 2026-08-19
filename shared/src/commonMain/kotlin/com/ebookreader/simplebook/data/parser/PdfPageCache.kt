package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

/**
 * 每本书的页面加载门面：缓存 → IO 渲染 → 回填缓存。
 * [load] 可安全地在任意线程/协程调用；单页渲染失败返回 null（UI 显示占位）。
 */
class PdfPageLoader(
    private val document: PdfDocument,
    private val cache: PdfPageCache,
    private val bookUuid: String
) : AutoCloseable {

    suspend fun load(index: Int, widthPx: Int): ImageBitmap? {
        // close 后 pageCount 可能抛异常，兜底为越界 → null
        val inBounds = runCatching { index in 0 until document.pageCount }.getOrDefault(false)
        if (!inBounds) return null
        val bucket = bucketize(widthPx)
        cache.get(bookUuid, index, bucket)?.let { return it }
        val bitmap = runCatching {
            withContext(Dispatchers.IO) { document.renderPage(index, bucket) }
        }.getOrNull() ?: return null
        cache.put(bookUuid, index, bucket, bitmap)
        return bitmap
    }

    /** 页宽高比（高/宽）；文档异常时回退 A4 竖版比例。 */
    fun aspectRatio(index: Int): Float = runCatching {
        val size = document.pageSizePts(index)
        if (size.width > 0f) size.height / size.width else DEFAULT_ASPECT_RATIO
    }.getOrNull() ?: DEFAULT_ASPECT_RATIO

    override fun close() {
        runCatching { document.close() }
    }

    companion object {
        /** A4 竖版（842/595），页面尺寸未知时的占位比例。 */
        const val DEFAULT_ASPECT_RATIO = 842f / 595f

        /** 宽度按 128px 桶量化，视口微变不致全量重渲染。 */
        fun bucketize(widthPx: Int): Int = ((widthPx + 127) / 128) * 128
    }
}

/** PDF 进度：当前页按页中点折算整体百分比。 */
fun pdfOverallPercentage(page: Int, pageCount: Int): Double =
    if (pageCount > 0) ((page + 0.5) / pageCount).coerceIn(0.0, 1.0) else 0.0

/** 页码钳制到 [0, pageCount-1]；页数未知（0）返回 0。 */
fun coercePdfPage(page: Int, pageCount: Int): Int =
    if (pageCount > 0) page.coerceIn(0, pageCount - 1) else 0
