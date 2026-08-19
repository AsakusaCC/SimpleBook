package com.ebookreader.simplebook.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File

class DesktopPdfTest {

    @Test
    fun openPdfReturnsDocumentWithPageCount() {
        val pdf = File.createTempFile("valid", ".pdf")
        writeMinimalPdf(pdf, pages = 3)
        try {
            val doc = openPdf(pdf)
            try {
                assertEquals(3, doc!!.pageCount)
            } finally {
                doc?.close()
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun pageSizePtsReportsA4Dimensions() {
        val pdf = File.createTempFile("size", ".pdf")
        writeMinimalPdf(pdf)
        try {
            openPdf(pdf)!!.use { doc ->
                assertEquals(595f, doc.pageSizePts(0).width, 1f)
                assertEquals(842f, doc.pageSizePts(0).height, 1f)
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun renderPageProducesBitmapAtRequestedWidth() {
        val pdf = File.createTempFile("render", ".pdf")
        writeMinimalPdf(pdf)
        try {
            openPdf(pdf)!!.use { doc ->
                val bmp = doc.renderPage(0, 200)
                assertEquals(200, bmp.width)
                // 高度按 A4 宽高比 842/595 ≈ 1.4147
                assertEquals(283, bmp.height)
            }
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun openPdfReturnsNullForGarbageFile() {
        val garbage = File.createTempFile("garbage", ".pdf").apply { writeBytes("not a pdf".toByteArray()) }
        try {
            assertNull(openPdf(garbage))
        } finally {
            garbage.delete()
        }
    }

    @Test
    fun openPdfReturnsNullForEmptyFile() {
        val empty = File.createTempFile("empty", ".pdf").apply { writeBytes(ByteArray(0)) }
        try {
            assertNull(openPdf(empty))
        } finally {
            empty.delete()
        }
    }

    @Test
    fun openPdfReturnsNullForTruncatedFile() {
        val full = File.createTempFile("full", ".pdf")
        writeMinimalPdf(full)
        val truncated = File.createTempFile("trunc", ".pdf")
        truncated.writeBytes(full.readBytes().copyOfRange(0, 50))
        try {
            assertNull(openPdf(truncated))
        } finally {
            full.delete(); truncated.delete()
        }
    }

    @Test
    fun openPdfWorksWithUseBlock() {
        // use 依赖 AutoCloseable —— 接口必须继承 AutoCloseable 才能这样写
        val pdf = File.createTempFile("use", ".pdf")
        writeMinimalPdf(pdf)
        try {
            openPdf(pdf)!!.use { doc ->
                assertTrue(doc.pageCount > 0)
            }
        } finally {
            pdf.delete()
        }
    }
}
