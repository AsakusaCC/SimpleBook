# SimpleBook 桌面端 EPUB 阅读视图设计文档

> 日期: 2026-07-21
> 状态: 已批准
> 分支: feat/kmp-desktop-migration

## 1. 背景与问题

### 1.1 现象

桌面端打开 EPUB 书籍阅读时，界面只显示占位文本 `EPUB reader not yet available on desktop`，无法阅读正文。

### 1.2 根因（调查纠正）

此前项目 memory 记载为「EPUB navMap NPE 导致同步失败」，经代码核查此判断**不准确**：

- navMap 的 `NullPointerException` 发生在 `epublib` 库内部（`NCXDocument.java:92`），被库自身的 `catch (Exception e) { log.error(...) }` 兜住，slf4j 不抛异常，`readEpub()` 正常返回，**不影响导入/同步**，仅是 stderr 一行噪音日志。
- 本地 54 本 EPUB 全部含有 navMap，无法复现该 NPE。
- 用户实际看到的「epub reader is not xxx」就是占位文本 `EPUB reader not yet available on desktop`（措辞记混）。

真正根因：`EpubReaderView.kt` 是一个空壳 stub，KMP 迁移时（commit `3520d87`）把原 Android 基于 WebView 的实现搬到 `commonMain` 时去掉了 WebView，只剩占位 `Text`。数据层（章节 HTML、样式、进度回调）已全部就绪，只缺渲染组件。

### 1.3 目标

实现 `EpubReaderView`：把 EPUB 章节 HTML 渲染为接近原书排版的富文本视图，支持滚动阅读、进度记录、章节切换、点击切工具栏。

### 1.4 影响范围

`EpubReaderView` 位于 `commonMain`，是唯一实现（无 `androidMain`/`desktopMain` actual）。本设计的新实现**同时用于 Android 与 Desktop**，顺带修复 Android 端迁移后同一 stub 导致的阅读不可用。

## 2. 核心决策

| 维度 | 决策 |
|------|------|
| 技术路线 | HTML → Compose 解析渲染（纯 Compose，无浏览器引擎） |
| 保真度 | 接近原书排版：标题/段落/加粗/斜体/图片/列表/引用/代码 |
| HTML 解析 | Ksoup（KMP 版 Jsoup，宽松容错） |
| 架构 | 分层：Parser（纯函数）/ Renderer（Composable）/ View（LazyColumn 编排） |
| 阅读模式 | 连续滚动（与 TxtReaderView 一致，复用进度回调契约） |
| 交付 | 单阶段交付核心元素；SVG/表格/嵌套列为已知限制 |

## 3. 架构与文件结构

全部位于 `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/`：

```
HtmlBlock.kt          # 纯数据模型（sealed class），无 Compose 依赖 → 可纯 JVM 单测
HtmlBlockParser.kt    # 纯函数：String(HTML) → List<HtmlBlock>，内部用 Ksoup
HtmlBlockRenderer.kt  # @Composable，渲染单个 HtmlBlock
EpubReaderView.kt     # 重写：LazyColumn 编排 + 复用 TxtReaderView 的进度/章末/tap 逻辑
```

边界原则：
- **Parser** 是纯函数，无 Compose 依赖，输入字符串输出数据，可独立单测。
- **Renderer** 无状态，只渲染单个 block，签名清晰。
- **View** 只负责把 block 列表喂给 `LazyColumn` 并接交互回调，不解析、不渲染细节。

三个单元均可独立理解和测试，互不依赖内部实现。

## 4. 数据模型

```kotlin
package com.ebookreader.simplebook.ui.reader

/** 行内文本片段（纯数据，无 Compose 依赖） */
data class InlineSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val href: String? = null
)

/** 一个块级元素内的行内内容 */
@JvmInline
value class InlineContent(val spans: List<InlineSpan>)

/** HTML 块级元素（章节内容解析结果） */
sealed class HtmlBlock {
    data class Heading(val level: Int, val content: InlineContent) : HtmlBlock()
    data class Paragraph(val content: InlineContent) : HtmlBlock()
    data class Image(val src: String, val alt: String?) : HtmlBlock()
    data class Quote(val content: InlineContent) : HtmlBlock()
    data class ListItem(val content: InlineContent, val ordered: Boolean, val index: Int) : HtmlBlock()
    data class CodeBlock(val text: String) : HtmlBlock()
    data object Rule : HtmlBlock()
}
```

支持元素 → `HtmlBlock` 映射：

| HTML | HtmlBlock |
|------|-----------|
| `h1`-`h6` | `Heading(level, …)` |
| `p` | `Paragraph(…)` |
| `img` | `Image(src, alt)`（src 已是 data URI） |
| `blockquote` | `Quote(…)` |
| `ul > li` | `ListItem(ordered=false, index=序号)` |
| `ol > li` | `ListItem(ordered=true, index=序号)` |
| `hr` | `Rule` |
| `pre` / `code` 块 | `CodeBlock(text)` |

行内元素 → `InlineSpan`：`b`/`strong`→bold，`i`/`em`→italic，`code`→code，`a`→href，`br`→拆分为新 span，纯文本节点→普通 span。HTML entity（`&amp;` 等）由 Ksoup 自动解码。

## 5. 数据流

```
Chapter.content (HTML, 图片已 data URI 化)
   │
   ▼  Ksoup.parse
DOM Document
   │
   ▼  HtmlBlockParser.parse(html)
List<HtmlBlock>
   │
   ▼  EpubReaderView (LazyColumn, items = blocks)
逐项调用 HtmlBlockRenderer
   │
   ▼
渲染：Heading/Paragraph → AnnotatedString；Image → 解码 data URI → Image
```

输入契约（`ReaderScreen` 已在传，无需改动）：
`htmlContent: String`、`initialScrollPercentage: Float`、`onScrollPercentageChanged`、`onChapterFinished`、`backgroundColor/textColor/accentColor: Long`、`fontSize/lineHeight: Float`、`hasNextChapter`、`nextChapterText`、`allReadText`、`onTap`、`modifier`。

## 6. 样式与图片

**样式映射**：
- `fontSize`/`lineHeight` → 容器 `TextStyle`（与 TxtReaderView 一致）。
- `backgroundColor` → 容器背景；`textColor` → 正文颜色。
- `accentColor` → 链接颜色 + 「下一章」按钮。
- Heading 字号随 level 递减（h1 最大，基于 `fontSize` 倍率）；Quote 带左侧色条 + 缩进；CodeBlock 用等宽字体 + 浅底。

**图片**：
- `src` 形如 `data:image/png;base64,xxxx` → Base64 解码为 `ByteArray` → `ImageBitmap` → `Image`。
- 支持：png / jpeg / gif / webp（Compose 原生解码器）。
- SVG：Compose 桌面无原生 SVG 解码，**跳过渲染**——有 `alt` 则显示 alt 文本，否则留空，列入已知限制。

## 7. 错误处理与降级

| 场景 | 处理 |
|------|------|
| 不规范 HTML | Ksoup 宽松解析，自动修复，不抛 |
| Parser 整体异常（极端输入） | try-catch，回退为单段纯文本 `Paragraph`（strip 所有标签），保证非空白 |
| 单张图片解码失败 | try-catch 跳过该图，不影响其余内容 |
| data URI 非图片格式 | 跳过 |
| 空章节 / 无 block | 显示 `allReadText` 或 ReaderScreen 的 `noContent`（已有兜底） |

核心原则：任何单点失败不阻塞整章渲染。

## 8. 测试策略

**`HtmlBlockParserTest`（desktopTest，纯函数测试）**：

- 段落 → `Paragraph`
- h1-h6 → `Heading(level)`
- `<img src="data:image/png;base64,...">` → `Image(src, alt)`
- `<ul><li>` / `<ol><li>` → `ListItem(ordered, index)`
- `<blockquote>` → `Quote`
- 行内 `<b>x</b><i>y</i>` → 同一 `InlineContent` 的多个 `InlineSpan`
- `<br>` 拆分
- `&amp;`/`&lt;` entity 解码
- `<hr>` → `Rule`
- 未知标签（如 `<span class="x">`）→ 内容保留为文本，不崩
- 空字符串 → 空列表
- 嵌套不深的情况下正确处理（`<p>text <b>bold</b> tail</p>`）

Parser 无 Compose 依赖，`./gradlew :shared:desktopTest` 快速跑。

**Renderer / View**：Compose 难单测，手动验证：
- `./gradlew :desktopApp:run` 打开真实 EPUB，验证排版、图片、滚动进度、章末对话框、tap 工具栏。
- 用本地 `~/Library/SimpleBook/books/` 的真实书籍（已知含 OEBPS/toc.ncx 标准 EPUB）。

## 9. 依赖

- 新增：`com.fleeksoft.ksoup:ksoup`（KMP，android + desktop JVM 均可用），加入 `shared/build.gradle.kts` 的 `commonMain` dependencies。
- 具体版本号在 writing-plans 阶段从 Maven Central 确认最新稳定版（约束：必须支持 Kotlin Multiplatform、android + jvm target）。
- 复用现有：Compose UI/Material3、`java.util.Base64`（已在用）。

## 10. 范围边界（本次不做）

以下为相关但独立的事项，不并入本次工作，避免范围蔓延：

- `ReaderViewModel.loadEpubChapters` / `BookService.importBook` 对损坏/空 epub 的 try-catch 防护。
- 0 字节 epub 文件 `6e4a76d0-...`（会在 readEpub 抛 ZipException，非本视图职责）。
- SVG 图片渲染、嵌套列表、HTML `<table>`、CSS class 样式。
- access_token 过期 refresh（独立的同步遗留项）。

## 11. 验收标准

1. 桌面端 `./gradlew :desktopApp:run` 打开任意本地 EPUB，不再显示占位文本，能看到正文排版。
2. 标题、段落、加粗、斜体、图片、列表、引用、代码块渲染正确（手动核对若干真实章节）。
3. 滚动阅读，进度百分比回调生效（切章再回来恢复到原位置）。
4. 滚到底触发「下一章」对话框；点击屏幕切换工具栏（与 TxtReaderView 行为一致）。
5. `HtmlBlockParserTest` 全部通过；`./gradlew :shared:desktopTest` 不回归。
6. Android 端打开 EPUB 同样可用（新实现位于 commonMain，共用）。
7. 损坏/不规范 HTML 不崩溃，降级显示纯文本。
