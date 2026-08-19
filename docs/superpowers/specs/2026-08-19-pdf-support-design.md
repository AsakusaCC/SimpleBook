# PDF 支持 v1（只读浏览）设计

- 日期：2026-08-19
- 状态：已批准（用户于头脑风暴会话中逐项确认）
- 范围：v1 只读浏览——导入、封面、纵向连续滚动阅读、适宽 + 双击缩放、页码进度、页级书签/笔记、Drive 同步。**不含**文本高亮、选词、PDF 内搜索、夜间反色渲染。

## 背景与目标

SimpleBook 当前整条阅读链路是「可重排文本」模型：`Parser → Chapter(content: String) → Compose LazyColumn 渲染文本块`，格式仅 EPUB/TXT。PDF 是固定版式（每页是带精确几何的画布），无法塞进该文本管道，需要一条独立的「页面渲染」路径。

目标（用户已确认的三个关键决策）：

1. 目标层级：v1 只读浏览（难度 ⭐⭐⭐，不做完整对齐 EPUB 体验）
2. 翻页交互：纵向连续滚动（与 EPUB/TXT 一致）
3. 缩放：适宽渲染 + 双击缩放（无捏合手势）
4. 接入方式：方案 A——ReaderScreen 按 format 分支 + 独立 `PdfReaderView`，书签/进度/TOC 复用现有管道

## 架构总览

```
BookFormat.PDF ─┬─ 导入: FileImportService/BookService 加 pdf 分支 + 封面渲染
                ├─ 阅读: ReaderViewModel PDF 分支 → PdfReaderView（新）
                │        工具栏/进度条/书签/笔记/TOC 侧板 原样复用（chapterIndex ≡ 页码）
                └─ 同步: SyncService mimeType 加 application/pdf（when 穷尽性编译器强制）
```

原则：不动 `Chapter` 文本模型；`chapterIndex` 对 PDF 的语义为页码（0-based）；**无 DB 迁移**（`ReadingProgress`/`Bookmark`/`Note` schema 原样复用）。

## 渲染层

### commonMain：`data/parser/PdfDocument.kt`

```kotlin
interface PdfDocument : AutoCloseable {
    val pageCount: Int
    fun pageSizePts(index: Int): SizeF          // 惰性读取，用于占位比例
    fun renderPage(index: Int, widthPx: Int): ImageBitmap   // 同步；调用方调度到 IO
}
expect fun openPdf(file: File): PdfDocument?    // null = 损坏/加密
```

接口化（而非直接 expect 类）是为了 ViewModel 单测可 fake。

### androidMain：系统 `PdfRenderer`

- `ParcelFileDescriptor.open(file, MODE_READ_ONLY)` → `PdfRenderer`，零新依赖（minSdk 26 满足，PdfRenderer 自 API 19 可用）
- `renderPage`：`openPage(index)` → 目标宽 × 宽高比 的 ARGB_8888 Bitmap → `page.render(bitmap, null, null, RENDER_MODE_FOR_DISPLAY)` → `asImageBitmap()`
- PdfRenderer 同一实例同时只能 open 一个 page，且非线程安全：`renderPage` 整体 `synchronized`
- 加密 PDF 构造时抛 `SecurityException` → `openPdf` 捕获后返回 null

### desktopMain：Apache PDFBox 3.x（仅 desktopMain 源集）

- `Loader.loadPDF(file)` → `PDFRenderer.renderImage(index, scale, ImageType.RGB)` → `toComposeImageBitmap()`
- 纯 Java 依赖，与现有「JVM 库进平台源集」策略一致（同 epublib/jsoup 模式）
- 已知限制：未嵌入字体的中文 PDF 可能缺字形（PDFBox 字体加载限制），v1 接受并记录

### 缓存：commonMain `PdfPageCache`

- 按字节估算的 LRU，上限约 64MB；key = (bookUuid, pageIndex, widthBucket)
- 渲染宽度 = 视口宽 × 1.6（清晰度采样），上限 2400px；双击 2x 档按 2× 视口宽重渲染（走同一缓存）
- 预取当前页 ±1

## UI 层

### `ui/reader/PdfReaderView.kt`（新）

- LazyColumn 纵向滚动，item = 页（白底位图 + 页码小标签「第 N 页」）
- 页面比例：打开时后台（IO 线程）预扫各页尺寸——Android `openPage` 不渲染仅读尺寸、PDFBox 读 MediaBox；UI 先按 A4 比例占位，扫到真实比例后修正（接受轻微滚动跳动）
- 双击：适宽 ↔ 2x 两档切换；放大档单页内 `horizontalScroll`
- 点击切换工具栏：复用 `EpubReaderView` 的 tap 检测模式（pointerInput + touchSlop）
- `onPageChanged`：由 `firstVisibleItemIndex` 推导当前页，上报 ViewModel

### ReaderViewModel / ReaderScreen 改动

- `loadBook()` 加 `BookFormat.PDF` 分支：`openPdf(File(book.filePath))`，失败 → `_loadError`（文案区分「文件可能已加密或损坏」）；成功 → 设置 pageCount、当前页 = `progress.chapterIndex`、TOC = 页码列表
- `goToChapter(页码)`、`toggleBookmark`（标题「第 N 页」）、`addNote`（页级锚点）原样复用
- 保存进度：`charOffset = 0`，`percentage = (页码 + 0.5) / 总页数`；去抖保存与 `onCleared` 立即保存复用现有逻辑
- `ReaderScreen`：`book?.format == PDF` 时渲染 `PdfReaderView`（在现有 `ChapterType` when 之前分支）
- 底栏进度区显示「第 X / N 页」；`TocPanel` 显示 `TocEntry("第 N 页", N-1)` 跳页列表（千页级 LazyColumn 无压力）
- 阅读主题仅作用于页间隙背景色；夜间反色渲染（ColorMatrix invert）留作后续增强

## 数据与同步

- `BookFormat` 枚举加 `PDF`
- `FileImportService.SUPPORTED_EXTENSIONS` 加 `"pdf"`
- `BookService.importBook`：`openPdf` 校验可打开且 `pageCount > 0` 后入库；title = 文件名（不用 PDF metadata，保持两端行为一致）；author = ""
- 封面：导入时渲染第 1 页（宽 ~600px）存 PNG 至 covers 目录，复用 `coverPath` 机制；保存动作为 expect `renderPdfCover(file: File, outFile: File): Boolean`（两端实现各约 15 行）
- `SyncService` 上传 mimeType 加 `BookFormat.PDF -> "application/pdf"`；进度/书签/笔记记录同步对 PDF 透明生效，无需额外代码

## 错误与边界

| 场景 | 行为 |
|---|---|
| 打不开/加密/损坏 | `openPdf` 返回 null → 复用现有 `loadError` 弹窗，文案「文件可能已加密或损坏」 |
| 单页渲染失败（OOM/坏页） | 该页显示「页面加载失败」占位，不影响其余页 |
| 超大 PDF（1000+ 页） | A4 占位 + 后台扫比例；LRU 缓存限制常驻内存 |
| 扫描版 PDF | 无影响（页面本就是位图） |
| 未嵌入字体（桌面） | 可能缺字形，v1 接受 |

## 测试（TDD，desktopTest）

1. `PdfPageCache`：LRU 淘汰、字节上限、预取（纯 common 逻辑）
2. 页码 ↔ 进度换算（percentage 公式）
3. `importBook` pdf 分支：PDFBox 现场生成 1 页最小 PDF 作 fixture，断言入库/拒绝损坏文件
4. ViewModel PDF 分支：fake `PdfDocument` 接口，测加载/跳页/进度保存
5. 桌面渲染 smoke：真开 fixture PDF，断言页数与位图尺寸
6. Android 端共享逻辑已由 common 测试覆盖，渲染路径靠桌面 smoke + 手工验证

## 明确不做（YAGNI）

- 文本高亮/选词/笔记锚定文本（Android 系统 PdfRenderer 无文本层；引入 pdfium/mupdf 的体积与 AGPL 许可代价不做）
- PDF 内搜索（同上依赖文本层）
- 捏合缩放、横向分页翻页模式
- 夜间反色渲染、PDF outline 目录（两端行为一致性优先，后续增强）
- PDF metadata 标题/作者提取
