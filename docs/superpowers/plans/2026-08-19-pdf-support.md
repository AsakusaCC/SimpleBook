# PDF 支持 v1（只读浏览）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** SimpleBook 支持 PDF 导入与只读浏览——纵向连续滚动、适宽 + 双击缩放、页码进度、页级书签/笔记、Drive 同步。

**架构：** 新增 `PdfDocument` 平台渲染接口（Android 系统 PdfRenderer / Desktop PDFBox，expect/actual）+ `PdfPageLoader`（IO 调度 + LRU 页缓存）。阅读接入采用方案 A：`ReaderScreen` 按 `book.format` 分支渲染新的 `PdfReaderView`，工具栏/进度条/书签/笔记/TOC 全部复用（`chapterIndex` ≡ 页码，0-based）。无 DB 迁移。

**技术栈：** Kotlin Multiplatform（androidTarget + jvm desktop）、Compose Multiplatform、`android.graphics.pdf.PdfRenderer`（Android，零新依赖）、Apache PDFBox 3.0.5（仅 desktopMain）、kotlin.test（desktopTest）。

**规格：** `docs/superpowers/specs/2026-08-19-pdf-support-design.md`

**验证命令约定：**
- 桌面测试：`./gradlew :shared:desktopTest`（单类过滤加 `--tests "com.ebookreader.simplebook.data.parser.XxxTest"`）
- Android 编译验证：`./gradlew :shared:compileDebugKotlin`
- 桌面运行手验：`./gradlew :desktopApp:run`

**测试基建说明：** 本项目没有 UI/ViewModel 测试基建（desktopTest 仅覆盖 parser/platform 纯逻辑），遵循现状：纯逻辑（缓存、loader、进度换算、导入）走 TDD；UI（任务 6-9）以编译 + 桌面运行手工验证为过关标准。

---

## 文件结构

**创建：**

| 文件 | 职责 |
|---|---|
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfDocument.kt` | 渲染接口 + `expect openPdf` + `expect ImageBitmap.encodeToPngBytes` + `writePdfCover` |
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCache.kt` | `ByteBudgetLruCache`（字节预算 LRU）+ `PdfPageCache` + `PdfPageLoader` + 进度纯函数 |
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/PdfReaderView.kt` | `PdfReaderState` + `PdfReaderView`（滚动/缩放/逐页渲染）|
| `shared/src/androidMain/kotlin/com/ebookreader/simplebook/data/parser/AndroidPdf.kt` | Android actual（PdfRenderer）|
| `shared/src/desktopMain/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdf.kt` | Desktop actual（PDFBox）|
| `shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfTestFixtures.kt` | 测试夹具：现场生成最小 PDF |
| `shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdfTest.kt` | 桌面渲染 smoke |
| `shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCacheTest.kt` | 缓存 + loader + 进度换算测试 |
| `shared/src/desktopTest/kotlin/com/ebookreader/simplebook/domain/service/BookServicePdfImportTest.kt` | PDF 导入分支测试 |

**修改：**

| 文件 | 变更 |
|---|---|
| `shared/build.gradle.kts` | desktopMain 加 PDFBox |
| `shared/src/commonMain/.../domain/model/BookFormat.kt` | 加 `PDF` |
| `shared/src/commonMain/.../domain/service/FileImportService.kt` | 白名单加 `pdf` |
| `shared/src/commonMain/.../domain/service/BookService.kt` | `importBook` PDF 分支 |
| `shared/src/commonMain/.../domain/service/SyncService.kt` | mimeType 加 `application/pdf` |
| `shared/src/commonMain/.../domain/model/AppStrings.kt` | 加 `pageOf` / `pageLabel`（en + zh）|
| `shared/src/commonMain/.../ui/reader/ReaderViewModel.kt` | PDF 加载/进度/书签分支 |
| `shared/src/commonMain/.../ui/reader/ReaderScreen.kt` | 渲染分支 + 底栏页码 |
| `shared/src/desktopMain/.../ui/components/DragDropOverlay.kt` | 拖拽白名单加 `pdf` |
| `androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt` | SAF mime 加 `application/pdf` |

---

### 任务 1：PdfDocument 接口 + 两平台实现 + PDFBox 依赖

**文件：**
- 修改：`shared/build.gradle.kts`
- 创建：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfDocument.kt`
- 创建：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdf.kt`
- 创建：`shared/src/androidMain/kotlin/com/ebookreader/simplebook/data/parser/AndroidPdf.kt`
- 测试：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfTestFixtures.kt`、`DesktopPdfTest.kt`

- [ ] **步骤 1：写失败的测试（夹具 + smoke）**

创建 `PdfTestFixtures.kt`：

```kotlin
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
```

创建 `DesktopPdfTest.kt`：

```kotlin
package com.ebookreader.simplebook.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test assertNull
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
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.DesktopPdfTest"`
预期：编译失败，报 `unresolved reference: openPdf`

- [ ] **步骤 3：加 PDFBox 依赖**

`shared/build.gradle.kts` 的 `desktopMain` dependencies 块内（`compose.desktop.currentOs` 附近）加：

```kotlin
                // PDF 渲染（Desktop JVM）——纯 Java；Android 端用系统 PdfRenderer，见 androidMain
                implementation("org.apache.pdfbox:pdfbox:3.0.5")
```

- [ ] **步骤 4：写 commonMain 接口**

创建 `PdfDocument.kt`：

```kotlin
package com.ebookreader.simplebook.data.parser

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File

/**
 * PDF 文档渲染接口。固定版式：页面按请求宽度渲染为位图（ImageBitmap 为 common 类型，
 * 平台实现内部负责 Bitmap/BufferedImage → ImageBitmap 转换）。
 *
 * 实现约定：
 * - [renderPage] / [pageSizePts] 是阻塞 IO/计算，调用方负责调度到后台线程（见 PdfPageLoader）；
 * - 实现内部需自行保证线程安全（同一实例的并发调用）；
 * - 0 ≤ index < pageCount，越界行为未定义（调用方已校验）。
 */
interface PdfDocument : AutoCloseable {
    val pageCount: Int

    /** 第 index 页的原始尺寸（PDF point，1/72 inch）。 */
    fun pageSizePts(index: Int): Size

    /** 以 widthPx 宽度渲染整页，返回位图（高度按页宽高比）。 */
    fun renderPage(index: Int, widthPx: Int): ImageBitmap
}

/** 打开 PDF；损坏 / 加密 / IO 失败返回 null。 */
expect fun openPdf(file: File): PdfDocument?

/** 编码为 PNG 字节；失败返回 null。用于封面落盘。 */
expect fun ImageBitmap.encodeToPngBytes(): ByteArray?
```

- [ ] **步骤 5：写 Desktop actual**

创建 `DesktopPdf.kt`：

```kotlin
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
        val pageWidth = doc.getPage(index).mediaBox.width.coerceAtLeast(1f)
        val scale = widthPx / pageWidth
        renderer.renderImage(index, scale, ImageType.RGB).toComposeImageBitmap()
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
```

- [ ] **步骤 6：写 Android actual**

创建 `AndroidPdf.kt`：

```kotlin
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
```

- [ ] **步骤 7：运行测试验证通过 + Android 编译**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.DesktopPdfTest"`
预期：7 个测试全 PASS

运行：`./gradlew :shared:compileDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 8：Commit**

```bash
git add shared/build.gradle.kts \
  shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfDocument.kt \
  shared/src/desktopMain/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdf.kt \
  shared/src/androidMain/kotlin/com/ebookreader/simplebook/data/parser/AndroidPdf.kt \
  shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfTestFixtures.kt \
  shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdfTest.kt
git commit -m "feat: PdfDocument 渲染接口（Android PdfRenderer / Desktop PDFBox）"
```

---

### 任务 2：PDF 封面生成

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfDocument.kt`
- 测试：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdfTest.kt`

- [ ] **步骤 1：写失败的测试**

在 `DesktopPdfTest.kt` 追加：

```kotlin
    @Test
    fun writePdfCoverCreatesPngFile() {
        val pdf = File.createTempFile("cover-src", ".pdf")
        writeMinimalPdf(pdf)
        val coversDir = File(createTempDir(), "covers")
        try {
            val cover = writePdfCover(pdf, coversDir)
            assertTrue(cover != null && cover.exists())
            assertEquals("png", cover!!.extension)
            // PNG 魔数 89 50 4E 47
            val head = cover.readBytes().copyOfRange(0, 4)
            assertTrue(head[0] == 0x89.toByte() && head[1] == 0x50.toByte() &&
                head[2] == 0x4E.toByte() && head[3] == 0x47.toByte())
        } finally {
            pdf.delete()
            coversDir.deleteRecursively()
        }
    }

    @Test
    fun writePdfCoverReturnsNullForGarbageFile() {
        val garbage = File.createTempFile("garbage-cover", ".pdf").apply { writeBytes(ByteArray(10)) }
        val coversDir = File(createTempDir(), "covers")
        try {
            assertNull(writePdfCover(garbage, coversDir))
        } finally {
            garbage.delete()
            coversDir.deleteRecursively()
        }
    }
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.DesktopPdfTest"`
预期：编译失败，报 `unresolved reference: writePdfCover`

- [ ] **步骤 3：实现**

在 `PdfDocument.kt` 末尾追加：

```kotlin
/** 封面渲染宽度（px），书架卡片足够清晰即可。 */
private const val PDF_COVER_WIDTH_PX = 600

/**
 * 渲染 PDF 第 1 页为封面 PNG 存入 [coversDir]（文件名时间戳，避免覆盖）。
 * 失败返回 null；阻塞 IO，调用方负责后台线程调度。
 */
fun writePdfCover(pdfFile: File, coversDir: File): File? = runCatching {
    openPdf(pdfFile)?.use { doc ->
        if (doc.pageCount <= 0) return null
        val bitmap = doc.renderPage(0, PDF_COVER_WIDTH_PX)
        val bytes = bitmap.encodeToPngBytes() ?: return null
        coversDir.mkdirs()
        val outFile = File(coversDir, "${System.currentTimeMillis()}.png")
        outFile.writeBytes(bytes)
        outFile
    }
}.getOrNull()
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.DesktopPdfTest"`
预期：9 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfDocument.kt \
  shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/DesktopPdfTest.kt
git commit -m "feat: PDF 封面生成（首页渲染存 PNG）"
```

---

### 任务 3：BookFormat.PDF + 导入链 + 同步 mimeType

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/BookFormat.kt`
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/FileImportService.kt:46`
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/BookService.kt:22-46`
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/SyncService.kt:172-174`
- 测试：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/domain/service/BookServicePdfImportTest.kt`

- [ ] **步骤 1：写失败的测试**

创建 `BookServicePdfImportTest.kt`：

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.parser.writeMinimalPdf
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class BookServicePdfImportTest {

    private class FakeBookRepository : BookRepository {
        val added = mutableListOf<Book>()
        override fun getAllBooks(): Flow<List<Book>> = emptyFlow()
        override suspend fun getAllBooksNow(): List<Book> = emptyList()
        override suspend fun getBookByUuid(uuid: String): Book? = null
        override suspend fun getBookByDriveFileId(driveFileId: String): Book? = null
        override suspend fun addBook(book: Book): String { added.add(book); return book.uuid }
        override suspend fun updateBook(book: Book) = error("not used in this test")
        override suspend fun softDeleteBook(uuid: String) = error("not used in this test")
        override suspend fun getAllBooksIncludingDeleted(): List<Book> = error("not used in this test")
        override suspend fun getDirtyBooks(): List<Book> = error("not used in this test")
        override suspend fun hardDeleteBook(uuid: String) = error("not used in this test")
        override fun getShelfBooks(): Flow<List<Book>> = emptyFlow()
        override fun getBooksInFolder(folderId: String): Flow<List<Book>> = emptyFlow()
        override suspend fun moveBookToFolder(bookUuid: String, folderId: String?) = error("not used in this test")
    }

    private fun service(repo: FakeBookRepository) =
        BookService(repo, EpubParser(), TxtParser())

    @Test
    fun importPdfCreatesBookWithCover() {
        val repo = FakeBookRepository()
        val pdf = File.createTempFile("import", ".pdf")
        writeMinimalPdf(pdf)
        try {
            val book = service(repo).importBook(pdf, "样例文档")
            assertEquals(1, repo.added.size)
            assertEquals(BookFormat.PDF, book.format)
            assertEquals("样例文档", book.title)
            assertEquals("", book.author)
            assertTrue(book.coverPath != null && File(book.coverPath).exists(),
                "cover file should exist at ${book.coverPath}")
        } finally {
            pdf.delete()
        }
    }

    @Test
    fun importGarbagePdfThrows() {
        val garbage = File.createTempFile("garbage", ".pdf").apply { writeBytes("junk".toByteArray()) }
        try {
            assertFailsWith<IllegalArgumentException> {
                service(FakeBookRepository()).importBook(garbage, "坏文件")
            }
        } finally {
            garbage.delete()
        }
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.domain.service.BookServicePdfImportTest"`
预期：编译失败（`BookFormat.PDF` 不存在 / when 分支缺失）

- [ ] **步骤 3：实现**

`BookFormat.kt` 全量替换为：

```kotlin
package com.ebookreader.simplebook.domain.model

enum class BookFormat {
    EPUB, TXT, PDF
}
```

`FileImportService.kt` 白名单改为：

```kotlin
        private val SUPPORTED_EXTENSIONS = setOf("epub", "txt", "pdf")
```

`BookService.kt`：
1. 文件头 import 区加：

```kotlin
import com.ebookreader.simplebook.data.parser.openPdf
import com.ebookreader.simplebook.data.parser.writePdfCover
import com.ebookreader.simplebook.platform.getBooksDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

2. `importBook` 的格式判定加分支（`"txt" -> ...` 之后）：

```kotlin
            "pdf" -> BookFormat.PDF
```

3. 元数据 when 加分支（`BookFormat.TXT -> {...}` 之后；`title/author/coverPath` 的 `var coverPath` 声明已存在）：

```kotlin
            BookFormat.PDF -> {
                val pageCount = withContext(Dispatchers.IO) {
                    openPdf(file)?.use { it.pageCount } ?: -1
                }
                if (pageCount <= 0) {
                    throw IllegalArgumentException("无法解析 PDF，文件可能已损坏或已加密: ${file.name}")
                }
                title = originalName
                author = ""
                coverPath = withContext(Dispatchers.IO) {
                    writePdfCover(file, File(getBooksDir(), "covers"))
                }?.absolutePath
            }
```

`SyncService.kt` 上传 mimeType when（约 172 行）加：

```kotlin
                        BookFormat.PDF -> "application/pdf"
```

- [ ] **步骤 4：运行测试验证通过 + 全量回归**

运行：`./gradlew :shared:desktopTest`
预期：全部 PASS（含既有测试）

运行：`./gradlew :shared:compileDebugKotlin`
预期：BUILD SUCCESSFUL（SyncService/BookService 的 when 穷尽性由编译器保证已补全）

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/BookFormat.kt \
  shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/FileImportService.kt \
  shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/BookService.kt \
  shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/SyncService.kt \
  shared/src/desktopTest/kotlin/com/ebookreader/simplebook/domain/service/BookServicePdfImportTest.kt
git commit -m "feat: PDF 纳入导入链（BookFormat/白名单/封面/Drive mimeType）"
```

---

### 任务 4：字节预算 LRU 页缓存

**文件：**
- 创建：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCache.kt`
- 测试：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCacheTest.kt`

- [ ] **步骤 1：写失败的测试**

创建 `PdfPageCacheTest.kt`（用 String 做值，`sizeOf = length`，完全避开 UI 类型，纯逻辑可测）：

```kotlin
package com.ebookreader.simplebook.data.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test assertNull
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
```

注意：`ImageBitmap(10, 10)` 是 compose common 工厂函数（桌面 JVM 上创建 Skia 栅格位图，无需显示设备）。`PdfPageCache` 的字节口径 `width * height * 4`：10×10 = 400 bytes < 1MB，不会触发淘汰。

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.ByteBudgetLruCacheTest"`
预期：编译失败，报 `unresolved reference: ByteBudgetLruCache`

- [ ] **步骤 3：实现**

创建 `PdfPageCache.kt`：

```kotlin
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
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.ByteBudgetLruCacheTest"`
预期：7 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCache.kt \
  shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCacheTest.kt
git commit -m "feat: PDF 页缓存（字节预算 LRU）"
```

---

### 任务 5：PdfPageLoader + 进度纯函数

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCache.kt`
- 测试：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCacheTest.kt`

- [ ] **步骤 1：写失败的测试**

在 `PdfPageCacheTest.kt` 追加测试类（同文件）：

```kotlin
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
```

同时在文件头 import 区补充：

```kotlin
import androidx.compose.ui.geometry.Size
import kotlin.test.assertNotNull
```

- [ ] **步骤 2：运行测试验证失败**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.PdfPageLoaderTest"`
预期：编译失败，报 `unresolved reference: PdfPageLoader`

- [ ] **步骤 3：实现**

在 `PdfPageCache.kt` 追加：

```kotlin
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
        if (index !in 0 until document.pageCount) return null
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
```

文件头 import 区补充：

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

- [ ] **步骤 4：运行测试验证通过**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.data.parser.PdfPageLoaderTest"`
预期：7 个测试全 PASS

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCache.kt \
  shared/src/desktopTest/kotlin/com/ebookreader/simplebook/data/parser/PdfPageCacheTest.kt
git commit -m "feat: PdfPageLoader（IO 调度+缓存门面）与页码进度纯函数"
```

---

### 任务 6：PdfReaderView（UI）

**文件：**
- 创建：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/PdfReaderView.kt`

本任务为纯 UI，无单测（项目无 UI 测试基建）；过关标准 = 编译通过 + 任务 8 接线后在任务 10 手工验证。

- [ ] **步骤 1：实现**

创建 `PdfReaderView.kt`：

```kotlin
package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.data.parser.PdfPageLoader
import com.ebookreader.simplebook.data.parser.coercePdfPage
import kotlinx.coroutines.flow.distinctUntilChanged

/** PDF 阅读会话状态：页数、加载器、后台预扫出的每页宽高比（高/宽）。 */
data class PdfReaderState(
    val pageCount: Int,
    val loader: PdfPageLoader,
    val aspectRatios: List<Float> = emptyList()
)

/**
 * PDF 纵向连续滚动阅读视图。
 * - 适宽渲染（视口宽 × [QUALITY_SCALE]）+ 双击切换 2x 档（单页内横向滚动）；
 * - 单击切换工具栏；当前页 = 首可见 item，经 [onPageChanged] 上报；
 * - [initialPage] 变化（TOC 跳页/进度恢复）时滚动到目标页。
 */
@Composable
fun PdfReaderView(
    state: PdfReaderState,
    initialPage: Int,
    pageLabel: (page: Int) -> String,
    pageLoadFailedText: String,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    backgroundColor: Long,
    modifier: Modifier = Modifier
) {
    var zoomed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 响应初始页恢复与外部跳页（TOC/书签/笔记）
    LaunchedEffect(listState, state) {
        snapshotFlow { initialPage }
            .distinctUntilChanged()
            .collect { page ->
                if (page != listState.firstVisibleItemIndex) {
                    listState.scrollToItem(coercePdfPage(page, state.pageCount))
                }
            }
    }

    // 当前页上报（首可见 item）
    LaunchedEffect(listState, state.pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> onPageChanged(page.coerceIn(0, state.pageCount - 1)) }
    }

    BoxWithConstraints(modifier = modifier.background(Color(backgroundColor))) {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
        val renderWidthPx = ((if (zoomed) viewportPx * 2f else viewportPx) * QUALITY_SCALE)
            .toInt()
            .coerceAtMost(MAX_RENDER_WIDTH_PX)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { zoomed = !zoomed }
                    )
                }
        ) {
            items(state.pageCount, key = { it }) { index ->
                PdfPageItem(
                    index = index,
                    aspectRatio = state.aspectRatios.getOrNull(index)
                        ?: PdfPageLoader.DEFAULT_ASPECT_RATIO,
                    renderWidthPx = renderWidthPx,
                    zoomed = zoomed,
                    viewportWidthPx = viewportPx,
                    loader = state.loader,
                    pageLabel = pageLabel,
                    pageLoadFailedText = pageLoadFailedText
                )
            }
        }
    }
}

/** 单页加载三态：渲染失败（文档关闭/坏页/OOM）单独呈现，不阻塞其他页。 */
private sealed interface PageState {
    data object Loading : PageState
    data class Ready(val bitmap: ImageBitmap) : PageState
    data object Failed : PageState
}

@Composable
private fun PdfPageItem(
    index: Int,
    aspectRatio: Float,
    renderWidthPx: Int,
    zoomed: Boolean,
    viewportWidthPx: Float,
    loader: PdfPageLoader,
    pageLabel: (page: Int) -> String,
    pageLoadFailedText: String
) {
    val pageState by produceState<PageState>(PageState.Loading, index, renderWidthPx) {
        val bmp = loader.load(index, renderWidthPx)
        value = if (bmp != null) PageState.Ready(bmp) else PageState.Failed
        // 预取下一页，滚动时直接命中缓存
        if (bmp != null) loader.load(index + 1, renderWidthPx)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pageContent: @Composable (Modifier) -> Unit = { m ->
            Box(m.aspectRatio(aspectRatio).background(Color.White)) {
                when (val ps = pageState) {
                    is PageState.Ready -> Image(
                        bitmap = ps.bitmap,
                        contentDescription = pageLabel(index + 1),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    PageState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    PageState.Failed -> Text(
                        text = pageLoadFailedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        if (zoomed) {
            // 2x 档：页面宽于视口，单页内横向滚动
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                pageContent(
                    Modifier.requiredWidth(
                        with(LocalDensity.current) { (viewportWidthPx * 2f).toDp() }
                    )
                )
            }
        } else {
            pageContent(Modifier.fillMaxWidth())
        }
        Text(
            text = pageLabel(index + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

private const val QUALITY_SCALE = 1.6f        // 适宽渲染的超采样系数（清晰度）
private const val MAX_RENDER_WIDTH_PX = 2400 // 位图宽度上限（内存护栏）
```

- [ ] **步骤 2：编译验证**

运行：`./gradlew :shared:compileDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/PdfReaderView.kt
git commit -m "feat: PdfReaderView（纵向滚动+双击缩放+逐页异步渲染）"
```

---

### 任务 7：ReaderViewModel PDF 分支

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt`

- [ ] **步骤 1：实现**

`ReaderViewModel.kt` 变更（保持既有代码风格，逐处修改）：

1. import 区加：

```kotlin
import com.ebookreader.simplebook.data.parser.PdfPageCache
import com.ebookreader.simplebook.data.parser.PdfPageLoader
import com.ebookreader.simplebook.data.parser.coercePdfPage
import com.ebookreader.simplebook.data.parser.openPdf
import com.ebookreader.simplebook.data.parser.pdfOverallPercentage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
```

2. 状态流区（`_notes` 声明之后）加：

```kotlin
    private val _pdfState = MutableStateFlow<PdfReaderState?>(null)
    val pdfState: StateFlow<PdfReaderState?> = _pdfState.asStateFlow()
```

3. `loadBook()` 的 `when (book.format)` 加分支：

```kotlin
                BookFormat.PDF -> loadPdf(book)
```

4. `loadBook()` 的进度恢复段（`readingService.loadProgress(bookUuid)?.let {...}`）整体替换为格式感知版本：

```kotlin
            readingService.loadProgress(bookUuid)?.let { progress ->
                val isPdf = book.format == BookFormat.PDF
                val pdfTotal = _pdfState.value?.pageCount ?: 0
                _currentChapterIndex.value = if (isPdf) {
                    coercePdfPage(progress.chapterIndex, pdfTotal)
                } else {
                    progress.chapterIndex
                }
                _scrollPercentage.value = when {
                    isPdf -> pdfOverallPercentage(_currentChapterIndex.value, pdfTotal).toFloat()
                    progress.charOffset > 0 -> (progress.charOffset / 10000.0).toFloat().coerceIn(0f, 1f)
                    else -> {
                        val totalChapters = _chapters.value.size
                        if (totalChapters > 0) {
                            ((progress.percentage * totalChapters) - progress.chapterIndex).toFloat().coerceIn(0f, 1f)
                        } else 0f
                    }
                }
            }
```

5. 新增 `loadPdf`（放在 `loadTxtChapters` 之后）：

```kotlin
    private suspend fun loadPdf(book: Book) {
        val doc = withContext(Dispatchers.IO) { openPdf(File(book.filePath)) }
        if (doc == null || doc.pageCount <= 0) {
            runCatching { doc?.close() }
            println("ReaderViewModel: failed to parse PDF (corrupt/encrypted?): ${book.filePath}")
            _loadError.value = "无法打开此书：文件可能已加密或损坏"
            return
        }
        val loader = PdfPageLoader(doc, PdfPageCache(), bookUuid)
        _pdfState.value = PdfReaderState(pageCount = doc.pageCount, loader = loader)
        _tocEntries.value = (0 until doc.pageCount).map {
            TocEntry(title = "第 ${it + 1} 页", chapterIndex = it)
        }
        // 后台预扫每页宽高比（未渲染仅读尺寸），供占位比例使用
        viewModelScope.launch(Dispatchers.IO) {
            val ratios = (0 until doc.pageCount).map { loader.aspectRatio(it) }
            _pdfState.value = _pdfState.value?.copy(aspectRatios = ratios)
        }
    }
```

6. `goToChapter` 的边界判断替换（PDF 用页数边界）：

```kotlin
    fun goToChapter(index: Int) {
        val inBounds = if (_book.value?.format == BookFormat.PDF) {
            index in 0 until (_pdfState.value?.pageCount ?: 0)
        } else {
            index in _chapters.value.indices
        }
        if (inBounds) {
            saveCurrentProgress()
            _currentChapterIndex.value = index
            _scrollPercentage.value = 0f
            refreshBookmarkStatus()
        }
    }
```

7. 新增页码上报入口（`onScrollPercentageChanged` 之后）：

```kotlin
    fun onPageChanged(page: Int) {
        _currentChapterIndex.value = page
        _scrollPercentage.value = pdfOverallPercentage(page, _pdfState.value?.pageCount ?: 0).toFloat()
        debounceSaveProgress()
    }
```

8. `toggleBookmark` 的章节标题取值替换为格式感知：

```kotlin
                val chapterTitle = if (_book.value?.format == BookFormat.PDF) {
                    "第 ${_currentChapterIndex.value + 1} 页"
                } else {
                    _chapters.value.getOrNull(_currentChapterIndex.value)?.title ?: ""
                }
```

9. `saveCurrentProgress` 整体替换：

```kotlin
    private fun saveCurrentProgress() {
        viewModelScope.launch {
            val isPdf = _book.value?.format == BookFormat.PDF
            val pdfTotal = _pdfState.value?.pageCount ?: 0
            val chapterPct = _scrollPercentage.value.toDouble().coerceIn(0.0, 1.0)

            val overallPct = when {
                isPdf -> pdfOverallPercentage(_currentChapterIndex.value, pdfTotal)
                else -> {
                    val totalChapters = _chapters.value.size
                    if (totalChapters > 0) {
                        ((_currentChapterIndex.value + chapterPct) / totalChapters).coerceIn(0.0, 1.0)
                    } else 0.0
                }
            }

            readingService.saveProgress(
                bookUuid = bookUuid,
                chapterIndex = _currentChapterIndex.value,
                charOffset = if (isPdf) 0L else (chapterPct * 10000).toLong(),
                percentage = overallPct
            )
        }
    }
```

10. `onCleared` 加 loader 释放（`saveJob?.cancel()` 之后）：

```kotlin
        _pdfState.value?.loader?.close()
```

- [ ] **步骤 2：编译验证**

运行：`./gradlew :shared:compileDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt
git commit -m "feat: ReaderViewModel PDF 分支（加载/进度/书签/释放）"
```

---

### 任务 8：ReaderScreen 接入 + AppStrings 页码文案

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/AppStrings.kt`
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderScreen.kt`

- [ ] **步骤 1：AppStrings 加两个文案键**

1. 数据类构造参数区，`chapterOf` 声明行之后加：

```kotlin
    val pageOf: (current: Int, total: Int) -> String,
    val pageLabel: (page: Int) -> String,
    val pageLoadFailed: String,
```

2. `getStrings` 的英文实例（`chapterOf = { current, total -> "Chapter $current of $total" },` 之后）加：

```kotlin
        pageOf = { current, total -> "Page $current of $total" },
        pageLabel = { page -> "Page $page" },
        pageLoadFailed = "Failed to load this page",
```

3. 中文实例（`chapterOf = { current, total -> "第 $current 章 / 共 $total 章" },` 之后）加：

```kotlin
        pageOf = { current, total -> "第 $current 页 / 共 $total 页" },
        pageLabel = { page -> "第 $page 页" },
        pageLoadFailed = "页面加载失败",
```

- [ ] **步骤 2：ReaderScreen 接线**

1. import 区加：

```kotlin
import com.ebookreader.simplebook.domain.model.BookFormat
```

2. `ReaderScreen` 中 `ReaderPane(...)` 调用加三个参数（`settings = settings,` 附近）：

```kotlin
            bookFormat = book?.format,
            pdfState = viewModel.pdfState.collectAsState().value,
            onPageChanged = viewModel::onPageChanged,
```

3. `ReaderPane` 签名加参数（`settings: ReaderSettings,` 之后）：

```kotlin
    bookFormat: BookFormat?,
    pdfState: PdfReaderState?,
    onPageChanged: (Int) -> Unit,
```

4. `ReaderPane` 内容区分支（`val currentChapter = chapters.getOrNull(currentChapterIndex)` 与 `when (currentChapter?.type)` 之间插入格式分支）：

```kotlin
            if (bookFormat == BookFormat.PDF && pdfState != null) {
                PdfReaderView(
                    state = pdfState,
                    initialPage = currentChapterIndex,
                    pageLabel = strings.pageLabel,
                    pageLoadFailedText = strings.pageLoadFailed,
                    onPageChanged = onPageChanged,
                    onTap = onToggleToolbar,
                    backgroundColor = settings.backgroundColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(settings.backgroundColor))
                )
            } else when (currentChapter?.type) {
                ChapterType.EPUB_HTML -> {
                    EpubReaderView(
                        htmlContent = currentChapter.content,
                        initialScrollPercentage = savedScrollPct,
                        onScrollPercentageChanged = onScrollPercentageChanged,
                        onChapterFinished = onNextChapter,
                        backgroundColor = settings.backgroundColor,
                        textColor = settings.textColor,
                        accentColor = settings.theme.accentColor,
                        fontSize = settings.fontSize,
                        lineHeight = settings.lineHeight,
                        hasNextChapter = currentChapterIndex < chapters.size - 1,
                        nextChapterText = strings.nextChapter,
                        allReadText = strings.allChaptersRead,
                        onTap = onToggleToolbar,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(settings.backgroundColor))
                    )
                }
                ChapterType.TXT_PLAIN -> {
                    TxtReaderView(
                        paragraphs = currentChapter.content.split("\n"),
                        initialScrollPercentage = savedScrollPct,
                        textStyle = textStyle,
                        onScrollPositionChanged = onScrollPercentageChanged,
                        onTap = onToggleToolbar,
                        hasNextChapter = currentChapterIndex < chapters.size - 1,
                        onNextChapter = onNextChapter,
                        nextChapterText = strings.nextChapter,
                        endOfChapterTitle = strings.endOfChapter,
                        continueQuestionText = strings.continueQuestion,
                        continueBtnText = strings.continueBtn,
                        stayBtnText = strings.stayBtn,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(settings.backgroundColor))
                    )
                }
                null -> {
                    Text(strings.noContent, modifier = Modifier.align(Alignment.Center))
                }
            }
```

（即把原 `when (currentChapter?.type) { ... }` 整体变为上述 `if/else when` 结构，else 分支内容与现有代码逐字相同。）
```

5. 底栏章节标题 Text 替换为格式感知（`text = currentChapter?.title ?: ""` 处）：

```kotlin
                    Text(
                        text = if (bookFormat == BookFormat.PDF) {
                            strings.pageLabel(currentChapterIndex + 1)
                        } else {
                            currentChapter?.title ?: ""
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
```

6. 底栏 `strings.chapterOf(...)` Text 替换为：

```kotlin
                    Text(
                        text = when {
                            bookFormat == BookFormat.PDF && pdfState != null ->
                                strings.pageOf(currentChapterIndex + 1, pdfState.pageCount)
                            else -> strings.chapterOf(currentChapterIndex + 1, chapters.size)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
```

- [ ] **步骤 3：编译验证**

运行：`./gradlew :shared:compileDebugKotlin && ./gradlew :shared:desktopTest`
预期：BUILD SUCCESSFUL，既有测试全 PASS

- [ ] **步骤 4：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/AppStrings.kt \
  shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderScreen.kt
git commit -m "feat: ReaderScreen 接入 PdfReaderView 与页码文案"
```

---

### 任务 9：两端导入入口

**文件：**
- 修改：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/ui/components/DragDropOverlay.kt:48`
- 修改：`androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt:192`

- [ ] **步骤 1：桌面拖拽白名单**

`DragDropOverlay.kt`：

```kotlin
    val supportedExt = remember { setOf("epub", "txt", "pdf") }
```

同时更新该文件两处注释中的 `(epub/txt)` 为 `(epub/txt/pdf)`（约 35 行与 120 行）。

- [ ] **步骤 2：Android SAF mime**

`MainActivity.kt` 192 行：

```kotlin
                                    importLauncher.launch(arrayOf("application/epub+zip", "text/plain", "application/pdf"))
```

- [ ] **步骤 3：编译验证**

运行：`./gradlew :shared:compileKotlinDesktop :androidApp:compileDebugKotlin`
预期：两个编译均 BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add shared/src/desktopMain/kotlin/com/ebookreader/simplebook/ui/components/DragDropOverlay.kt \
  androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt
git commit -m "feat: 导入入口支持 PDF（桌面拖拽 + Android SAF）"
```

---

### 任务 10：全量验证

**文件：** 无新增（验证任务）

- [ ] **步骤 1：自动化验证**

```bash
./gradlew :shared:desktopTest
./gradlew :androidApp:assembleDebug
```

预期：desktopTest 全 PASS；APK 构建成功。

- [ ] **步骤 2：桌面手工验证**

运行：`./gradlew :desktopApp:run`，用一本真实 PDF（准备一个多页 PDF，含图与文字各一页更佳）：

1. 拖入 PDF → 书架出现卡片（封面 = 第 1 页缩略图，标题 = 文件名）
2. 打开 → 纵向滚动浏览，页面连续、白底、下方页码标签
3. 单击 → 工具栏出现/隐藏；双击 → 2x 放大 + 单页横向滚动；再双击 → 恢复适宽
4. 滚动到中间页 → 退出重进 → 停留在原页（进度持久化）
5. TOC 面板 → 点「第 N 页」→ 跳页成功
6. 加书签 → 书签列表点回跳页；加笔记 → 侧板可见
7. 底栏显示「第 X 页 / 共 N 页」，进度条随滚动推进
8. 拖入一个改名 .pdf 的垃圾文件 → 书架无新增（导入静默跳过，与 EPUB 行为一致）
9. 打开一个加密 PDF（可临时用任意工具生成）→ 「无法打开此书：文件可能已加密或损坏」弹窗 → 返回书架
10. EPUB/TXT 回归：各打开一本，翻页/进度/书签正常

- [ ] **步骤 3：Android 手工验证**

安装 debug APK，重复桌面清单 1-7、9-10（SAF 选择器能看到并选中 PDF）。

- [ ] **步骤 4：同步验证（可选，若已配置 Google 账号）**

桌面同步上传 PDF → 另一端同步下载 → 打开阅读一致（书文件、进度、书签记录）。

- [ ] **步骤 5：Commit（如有验证中修的零星问题）**

```bash
git add -A
git commit -m "fix: PDF v1 验证修复"
```

---

## 已知限制（继承自规格，非缺陷）

- 未嵌入字体的中文 PDF 在桌面端可能缺字形（PDFBox 字体加载限制）
- 加密 PDF 仅报错不支持打开（不做密码输入）
- 阅读主题只影响页间隙背景，PDF 页面本身白底不变（夜间反色是后续增强）
- 1000+ 页大 PDF：打开时后台逐页读尺寸会有一次性 IO 开销；滚动中占位比例被真实比例替换时可能有轻微跳动
