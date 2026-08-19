package com.ebookreader.simplebook.data.parser

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.common.PDRectangle
import java.io.File

/** 用 PDFBox 现场生成最小合法 PDF（无内容页），A4 = 595×842 pt。 */
fun writeMinimalPdf(file: File, pages: Int = 2) {
    PDDocument().use { doc ->
        repeat(pages) { doc.addPage(PDPage(PDRectangle.A4)) }
        doc.save(file)
    }
}
