package com.ebookreader.simplebook.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.io.File

actual fun openPdf(file: File): PdfDocument? = try {
    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    try {
        AndroidPdfDocument(PdfRenderer(pfd), pfd)
    } catch (e: Exception) {
        runCatching { pfd.close() }
        null
    }
} catch (_: Exception) {
    null
}

private class AndroidPdfDocument(
    private val renderer: PdfRenderer,
    private val pfd: ParcelFileDescriptor
) : PdfDocument {

    override val pageCount: Int get() = renderer.pageCount

    override fun pageSizePts(index: Int): Size = synchronized(renderer) {
        // 注意：不用 page.use{}——PdfRenderer.Page 的 AutoCloseable 接口是 API 35 才加的，
        // use 在 API 26-34 运行时会走 invokeinterface 直接崩；手写 try/finally 调 close() 才安全。
        val page = renderer.openPage(index)
        try {
            Size(page.width.toFloat(), page.height.toFloat())
        } finally {
            page.close()
        }
    }

    override fun renderPage(index: Int, widthPx: Int): ImageBitmap = synchronized(renderer) {
        // PdfRenderer 同一实例同时只能 open 一个 page，且非线程安全——整体持锁
        val page = renderer.openPage(index)
        try {
            val ratio = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
            val height = (widthPx * ratio).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE) // PDF 空白区域是透明的，铺白底
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap.asImageBitmap()
        } finally {
            page.close()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
    }
}

actual fun ImageBitmap.encodeToPngBytes(): ByteArray? = try {
    val out = ByteArrayOutputStream()
    if (asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
} catch (_: Exception) {
    null
}
