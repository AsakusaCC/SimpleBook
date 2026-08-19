package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.math.roundToInt

actual fun openPdf(file: File): PdfDocument? = try {
    DesktopPdfDocument(Loader.loadPDF(file))
} catch (_: Exception) {
    null
}

private class DesktopPdfDocument(private val doc: PDDocument) : PdfDocument {
    private val renderer = PDFRenderer(doc)

    override val pageCount: Int get() = doc.numberOfPages

    override fun pageSizePts(index: Int): Size {
        val box = doc.getPage(index).mediaBox
        return Size(box.width, box.height)
    }

    override fun renderPage(index: Int, widthPx: Int): ImageBitmap = synchronized(doc) {
        val page = doc.getPage(index)
        val pageWidth = page.mediaBox.width.coerceAtLeast(1f)
        val ratio = page.mediaBox.height / pageWidth
        val height = (widthPx * ratio).roundToInt().coerceAtLeast(1)
        val scale = widthPx / pageWidth
        // Render into BufferedImage of exact size to match test expectation
        val awtImage = renderer.renderImage(index, scale, ImageType.RGB)
        val target = java.awt.image.BufferedImage(widthPx, height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        target.graphics.drawImage(awtImage, 0, 0, widthPx, height, null)
        target.toComposeImageBitmap()
    }

    override fun close() {
        runCatching { doc.close() }
    }
}

actual fun ImageBitmap.encodeToPngBytes(): ByteArray? = try {
    org.jetbrains.skia.Image.makeFromBitmap(asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)
        ?.bytes
} catch (_: Exception) {
    null
}
