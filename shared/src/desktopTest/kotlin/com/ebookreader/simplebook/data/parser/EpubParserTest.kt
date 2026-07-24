package com.ebookreader.simplebook.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubParserTest {

    @Test
    fun parseReturnsResultForValidMinimalEpub() {
        val epub = File.createTempFile("valid", ".epub")
        writeMinimalEpub(epub, "My Test Title")
        try {
            val result = EpubParser().parse(epub)
            assertNotNull(result)
            assertEquals("My Test Title", result!!.title)
            assertTrue(result.chapterCount >= 1)
        } finally {
            epub.delete()
        }
    }

    @Test
    fun parseReturnsNullForTruncatedEpub() {
        // 真实崩溃源：下载中断/写入不完整产生的截断 zip，
        // epublib.readEpub 会抛 EOFException —— parse 应兜底返回 null。
        val full = File.createTempFile("full", ".epub")
        writeMinimalEpub(full)
        val truncated = File.createTempFile("trunc", ".epub")
        val bytes = full.readBytes()
        truncated.writeBytes(bytes.copyOfRange(0, bytes.size / 2))
        try {
            assertNull(EpubParser().parse(truncated))
        } finally {
            full.delete()
            truncated.delete()
        }
    }

    @Test
    fun parseReturnsNullForEmptyFile() {
        // epublib 对 0 字节文件返回空 Book（不抛），但空书无正文可读 ——
        // parse 应把它当作无法阅读返回 null，让调用方提示用户而非显示空白页。
        val empty = File.createTempFile("empty", ".epub").apply { writeBytes(ByteArray(0)) }
        try {
            assertNull(EpubParser().parse(empty))
        } finally {
            empty.delete()
        }
    }

    /** 构造一个 epublib 能成功解析的最小合法 EPUB（spine 含 1 章）。 */
    private fun writeMinimalEpub(file: File, title: String = "Test Book") {
        ZipOutputStream(file.outputStream()).use { zos ->
            // mimetype 必须是首个条目且 STORED（不压缩）
            val mime = "application/epub+zip".toByteArray()
            val mimeEntry = ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mime.size.toLong()
                compressedSize = mime.size.toLong()
                crc = CRC32().apply { update(mime) }.value
            }
            zos.putNextEntry(mimeEntry); zos.write(mime); zos.closeEntry()

            putEntry(zos, "META-INF/container.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles>
                    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
                  </rootfiles>
                </container>
            """.trimIndent())

            putEntry(zos, "OEBPS/content.opf", """
                <?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="bookid">
                  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                    <dc:title>$title</dc:title>
                    <dc:identifier id="bookid">urn:uuid:1</dc:identifier>
                    <dc:language>en</dc:language>
                  </metadata>
                  <manifest>
                    <item id="c1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                  </manifest>
                  <spine><itemref idref="c1"/></spine>
                </package>
            """.trimIndent())

            putEntry(zos, "OEBPS/chapter.xhtml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Ch1</title></head>
                <body><p>Hello world</p></body></html>
            """.trimIndent())
        }
    }

    private fun putEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray())
        zos.closeEntry()
    }
}
