# EPUB 内 SVG 图片渲染 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让阅读器能显示 EPUB 内的 SVG 形态图片（svg-wrapped 位图 + 独立 SVG 文件），修复 Mac 端 65 本书图片不显示的问题。

**架构：** 在现有"epublib 解析 → `resolveImageReferences` 生成 data URI → `HtmlBlockParser` 转 `HtmlBlock` → `HtmlBlockRenderer` 用 Coil `AsyncImage` 渲染"管线上做两件事：(1) 让 `HtmlBlockParser` 识别 SVG 内的 `<image>` 标签（核心修复，让 svg-wrapped 位图进入渲染）；(2) 引入 coil-svg + 注册 `SvgDecoder`，让真 SVG 矢量也能解码；并删掉 Renderer 里对 SVG 的跳过分支。

**技术栈：** Kotlin Multiplatform（shared: commonMain / androidMain / desktopMain）、Compose Multiplatform、Coil3 3.0.4（+ coil-svg 3.0.4）、jsoup、kotlin.test（desktopTest source set）。

**设计规格：** [docs/superpowers/specs/2026-07-24-epub-svg-rendering-design.md](../specs/2026-07-24-epub-svg-rendering-design.md)

**环境前置（所有 gradle 命令前需执行一次）：**
```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```
桌面测试 task：`./gradlew :shared:desktopTest`（desktopTest source set，kotlin.test 框架）。

---

## 文件结构

| 文件 | 职责 | 操作 |
|---|---|---|
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParser.kt` | HTML → `HtmlBlock` 解析（纯函数） | 修改：新增 `image` case |
| `shared/src/desktopTest/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParserTest.kt` | Parser 单测 | 修改：新增 svg `<image>` 用例 |
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt` | 章节 HTML 引用解析 | 修改：`svgImageRegex` 补 `href` |
| `shared/build.gradle.kts` | 依赖声明 | 修改：+coil-svg |
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/ImageLoaderSetup.kt` | 全局 ImageLoader（SvgDecoder） | 新建 |
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt` | 两端共享入口 | 修改：挂载 ImageLoader |
| `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockRenderer.kt` | `HtmlBlock` 渲染 | 修改：删 SVG 跳过分支 |

---

## 任务 1：HtmlBlockParser 识别 SVG 内 `<image>` 标签（核心，TDD）

**这是核心修复** —— 让 svg-wrapped 位图（Mac 端全部 SVG 形态）进入渲染链路。纯函数，TDD。

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParser.kt`
- 测试：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParserTest.kt`

- [ ] **步骤 1：编写失败的测试**

在 `HtmlBlockParserTest` 类内（建议加在 `parsesImageWithDataUri` 之后）追加两个用例：

```kotlin
@Test
fun parsesSvgWrappedImageXlinkHref() {
    // 真实样本写法：图片型 EPUB 每页一张 jpg，用 svg 包裹做尺寸适配
    val html = """
        <svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
             width="100%" height="100%" viewBox="0 0 867 1300">
            <image width="867" height="1300" xlink:href="../Images/200273.jpg"/>
        </svg>
    """.trimIndent()
    val blocks = HtmlBlockParser.parse(html)
    val img = blocks.filterIsInstance<HtmlBlock.Image>().single()
    assertEquals("../Images/200273.jpg", img.src)
}

@Test
fun parsesSvgImageBareHref() {
    // SVG2 写法（裸 href，无 xlink: 前缀）
    val blocks = HtmlBlockParser.parse("<svg><image href=\"cover.png\"/></svg>")
    val img = blocks.filterIsInstance<HtmlBlock.Image>().single()
    assertEquals("cover.png", img.src)
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：
```bash
./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.ui.reader.HtmlBlockParserTest.parsesSvgWrappedImageXlinkHref" --tests "com.ebookreader.simplebook.ui.reader.HtmlBlockParserTest.parsesSvgImageBareHref"
```
预期：FAIL —— `filterIsInstance<HtmlBlock.Image>().single()` 抛 `NoSuchElementException`（Parser 不识别 `<image>`，产出 0 个 Image 块）。

- [ ] **步骤 3：编写最少实现代码**

在 `HtmlBlockParser.kt` 的 `collectBlocks` 函数中，`is Element -> when (node.tagName().lowercase())` 分支内，紧接 `"img" ->` case（约 L59-64）之后新增 `"image"` case：

```kotlin
"image" -> out.add(
    HtmlBlock.Image(
        // SVG <image> 用 xlink:href（SVG1）或 href（SVG2）；jsoup 保留命名空间前缀属性名
        src = node.attr("xlink:href").takeIf { it.isNotBlank() } ?: node.attr("href"),
        alt = node.attr("alt").takeIf { it.isNotBlank() }
    )
)
```

> 说明：`<image>` 位于 `<svg>` 内。`collectBlocks` 遇到 `svg` 走 `else` 递归子节点，自然命中此 case，无需单独处理 `svg` 标签。

- [ ] **步骤 4：运行测试验证通过**

运行：
```bash
./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.ui.reader.HtmlBlockParserTest"
```
预期：PASS（全部 Parser 测试，含两个新用例）。

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParser.kt \
        shared/src/desktopTest/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParserTest.kt
git commit -m "feat(reader): 解析 SVG 内 <image> 标签为 HtmlBlock.Image

修复图片型 EPUB（svg-wrapped 位图）完全不渲染的问题。"
```

---

## 任务 2：ReaderViewModel 的 SVG `<image>` 引用解析兼容裸 href

现有正则只匹配 `xlink:href`，补 `href`（SVG2 写法）。此方法为 `private` 且依赖 `epublib.Book`，无法用现有 desktopTest 基础设施单测；`xlink:href` 路径（Mac 端真实书）在任务 5 集成验收覆盖，`href` 路径依赖代码审查 + 未来 SVG2 书籍验证。

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt`（`resolveImageReferences` 内 `svgImageRegex`，约 L224）

- [ ] **步骤 1：修改正则**

将：
```kotlin
val svgImageRegex = Regex("""(<image\s[^>]*?xlink:href\s*=\s*["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
```
改为：
```kotlin
val svgImageRegex = Regex("""(<image\s[^>]*?(?:xlink:)?href\s*=\s*["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
```

> `(?:xlink:)?href` 同时匹配 `xlink:href`（SVG1）与裸 `href`（SVG2）；捕获组结构不变，替换逻辑无需改动。

- [ ] **步骤 2：编译验证**

运行：
```bash
./gradlew :shared:compileKotlinDesktop
```
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt
git commit -m "feat(reader): SVG <image> 引用解析兼容裸 href（SVG2）"
```

---

## 任务 3：引入 coil-svg 并注册全局 SvgDecoder

让真 SVG 矢量图（形态 A）也能解码；为任务 4 删除 SVG 跳过分支做铺垫。Android 后端 = androidsvg，桌面 JVM 后端 = Skiko。

**文件：**
- 修改：`shared/build.gradle.kts`
- 创建：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/ImageLoaderSetup.kt`
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt`

- [ ] **步骤 1：添加 coil-svg 依赖**

在 `shared/build.gradle.kts` 的 `commonMain.dependencies` 块内，紧接现有 coil 依赖（约 L73 `implementation("io.coil-kt.coil3:coil-compose:3.0.4")`）之后追加：

```kotlin
// Coil SVG decoder — 解码 SVG（Android: androidsvg；Desktop JVM: Skiko）
implementation("io.coil-kt.coil3:coil-svg:3.0.4")
```

- [ ] **步骤 2：解析依赖并编译**

运行：
```bash
./gradlew :shared:compileKotlinDesktop
```
预期：BUILD SUCCESSFUL（coil-svg 3.0.4 被解析，Skiko 版本由 Gradle 自动对齐到 CMP 已有版本）。

> **若构建报 Skiko 版本冲突**（低概率）：运行 `./gradlew :shared:dependencies --configuration desktopRuntimeClasspath | grep skiko` 对比 coil-svg 拉入版本与 CMP 版本，用 constraints 强制对齐到 CMP 版本。此为备选排查，主路径应直接通过。

- [ ] **步骤 3：创建全局 ImageLoader 配置**

新建 `shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/ImageLoaderSetup.kt`：

```kotlin
package com.ebookreader.simplebook.ui

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.svg.SvgDecoder

/**
 * 注册全局 ImageLoader，启用 SVG 解码（SvgDecoder）。
 *
 * 幂等：可重复调用。在 App 启动时调一次（见 [com.ebookreader.simplebook.App]）。
 * Android 后端 = androidsvg，桌面 JVM 后端 = Skiko（均由 coil-svg 提供）。
 */
fun setupImageLoader() {
    SingletonImageLoader.setSafe {
        ImageLoader.Builder().components {
            add(SvgDecoder.Factory())
        }.build()
    }
}
```

- [ ] **步骤 4：在 App 入口挂载**

修改 `shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt`：

顶部新增 import：
```kotlin
import com.ebookreader.simplebook.ui.setupImageLoader
```

在 `App(...)` 函数体第一行（`val settingsViewModel ...` 之前）插入：
```kotlin
    // 注册全局 ImageLoader（含 SvgDecoder）。remember 保证每 composition 只调一次；setSafe 自身也幂等。
    remember { setupImageLoader() }
```

- [ ] **步骤 5：编译验证**

运行：
```bash
./gradlew :shared:compileKotlinDesktop
```
预期：BUILD SUCCESSFUL。

- [ ] **步骤 6：Commit**

```bash
git add shared/build.gradle.kts \
        shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/ImageLoaderSetup.kt \
        shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt
git commit -m "feat: 引入 coil-svg 并注册全局 SvgDecoder"
```

---

## 任务 4：HtmlBlockRenderer 删除 SVG 跳过分支，统一走 AsyncImage

配合任务 3，让 SVG data URI 也走 `AsyncImage`（由 SvgDecoder 解码）。

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockRenderer.kt`（`BlockImage`，L114-136）

- [ ] **步骤 1：替换 BlockImage 实现**

将整个 `BlockImage` 函数（L114-133）替换为：

```kotlin
@Composable
private fun BlockImage(block: HtmlBlock.Image, modifier: Modifier) {
    // 统一走 AsyncImage：位图走 Coil 默认解码器；SVG data URI 走 SvgDecoder（见 ImageLoaderSetup）。
    AsyncImage(
        model = block.src,
        contentDescription = block.alt,
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}
```

- [ ] **步骤 2：删除不再使用的 isSvgDataUri**

删除 `HtmlBlockRenderer.kt` 中的 `isSvgDataUri` 私有函数（原 L135-136）：

```kotlin
// 删除：
private fun isSvgDataUri(src: String): Boolean =
    src.startsWith("data:image/svg", ignoreCase = true)
```

- [ ] **步骤 3：编译验证**

运行：
```bash
./gradlew :shared:compileKotlinDesktop
```
预期：BUILD SUCCESSFUL（无未使用函数/警告阻断）。

- [ ] **步骤 4：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockRenderer.kt
git commit -m "refactor(reader): BlockImage 统一走 AsyncImage，删除 SVG 跳过分支"
```

---

## 任务 5：双端集成验收 + 全量回归

用 Mac 端真实书验收。**不 commit 代码**（本任务为验证，通过则全部任务完成）。

- [ ] **步骤 1：全量 desktopTest 回归**

运行：
```bash
./gradlew :shared:desktopTest
```
预期：所有测试 PASS（含原 18 个 Parser 测试 + 任务 1 新增 2 个 + 其他模块测试，无回归）。

- [ ] **步骤 2：桌面端运行验收**

运行（启动桌面 app）：
```bash
./gradlew :desktopApp:run
```
在打开的窗口中：
1. 打开书架里任一图片型 EPUB（如对应 `~/Library/SimpleBook/books/44884637-bdb7-4561-941a-220041b038df.epub` 的那本，18 张 svg-wrapped jpg）。
2. 翻到正文，**确认每页的图正常显示**（改造前是完全空白）。
3. 打开一本纯文字 EPUB，确认文字渲染不回归。
4. 打开一本含普通 png/jpg 插图的 EPUB，确认位图渲染不回归。

- [ ] **步骤 3：Android 端构建验收**

运行：
```bash
./gradlew :shared:assembleDebug
```
预期：BUILD SUCCESSFUL（确认 Android 端编译通过；SvgDecoder.Factory 在 androidMain 可用）。

> 若有连接的设备/模拟器，进一步 `./gradlew :androidApp:installDebug` 安装并在 Android 端重复步骤 2 的验收。无设备时步骤 3 的编译通过即可作为 Android 端最低保障。

- [ ] **步骤 4：记录验收结果**

在 PR 描述或提交说明里记录：验收书目、双端图片显示情况、回归检查结果。

---

## 验收标准（对应 spec §7.3）

1. ✅ 打开 `44884637` 这类图片型 EPUB → 所有页面图正常显示（Android + 桌面）
2. ✅ 原 png/jpeg 插图渲染不回归
3. ✅ `HtmlBlockParser` 单元测试通过（任务 1）
4. ✅ 全量 `desktopTest` 无回归（任务 5 步骤 1）

## 风险（对应 spec §8）

- **Skiko 版本对齐**：任务 3 步骤 2 若报冲突，按备选排查步骤对齐。
- **复杂 SVG 降级**：`foreignObject`/外部引用/CSS 动画可能渲染不全 → 静默降级，可接受。
- **图片型 EPUB 全页图比例**：`AsyncImage` 默认 `ContentScale.Fit` 保留比例；若验收发现极端宽高比图显示异常，作为独立问题处理（不纳入本次范围）。
