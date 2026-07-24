# EPUB 内 SVG 图片渲染改造 — 设计规格

- 日期：2026-07-24
- 状态：已确认方案，待实现
- 范围：`shared`（commonMain 为主）、桌面 + Android 双端

## 1. 背景与问题

阅读器用**原生 Compose**渲染 EPUB（非 WebView），图片走 Coil3 `AsyncImage`。当前 EPUB 内所有 SVG 形态的图片都显示不出来。

渲染管线：

```
epublib 解析 EPUB
  → ReaderViewModel.resolveImageReferences() 把 <img src> 与 <image xlink:href> 重写成 base64 data URI
  → HtmlBlockParser (jsoup) 把章节 HTML 转成 List<HtmlBlock>
  → EpubReaderView 在 LazyColumn 逐项调 HtmlBlockRenderer 渲染
```

链路中有三处断裂（详见诊断 §3）。

## 2. 目标与范围

**必做（修复现有书籍）：**
- SVG 包裹位图：`<svg ...><image xlink:href="...jpg/png"/></svg>`。这是 Mac 端 65 本书的**全部** SVG 形态。

**配套（让 SVG 支持完整，建议同批完成）：**
- 独立 SVG 文件：`<img src="x.svg">`（当前书目里不存在，但属"SVG 支持应有之义"）。

**不做（YAGNI）：**
- 内联矢量图：`<svg>...</svg>` 直接画矢量图形/图标/图表。需改 Parser 识别整块 SVG 并单独渲染，复杂度最高，当前需求不涉及。

## 3. 现状诊断（三处断裂）

| # | 位置 | 现状 | 后果 |
|---|---|---|---|
| 1 | [HtmlBlockParser.kt:45](shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParser.kt#L45) `collectBlocks` | 只识别 `img`，不识别 `image`（svg 内）；遇到 `svg` 走 else 递归，遇到 `image` 又走 else 递归，无子节点 → 不产出任何块 | **核心断裂**：svg-wrapped 位图完全不进入渲染链路 |
| 2 | [ReaderViewModel.kt:223-228](shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt#L223-L228) `resolveImageReferences` | 已用正则把 `<image xlink:href>` 解析成 data URI | 解析成果被 #1 丢弃，**白做** |
| 3 | [HtmlBlockRenderer.kt:116](shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockRenderer.kt#L116) `BlockImage` | `isSvgDataUri` 守卫直接跳过 SVG，只显示 alt | 真 SVG 矢量图（形态 A）即便引入解码器也进不去 |

## 4. 真实样本验证（Mac 端 65 本已导入书）

对 `~/Library/SimpleBook/books/*.epub` 全量统计：

- `svg` 文件数 = **0**；`<img src=*.svg>` 引用 = **0**（形态 A 不存在）
- `<image>` 标签数 恒等于 内联 `<svg>` 数（每本 1–30 个）→ **100% 为 svg-wrapped 位图**
- `<image>` 用 `xlink:href`（18/18，无裸 href）
- 引用资源全为 `.jpg`（全页图，图片型 EPUB）

样本写法：

```xhtml
<svg xmlns="http://www.w3.org/2000/svg" xmlns:xlink="http://www.w3.org/1999/xlink"
     width="100%" height="100%" viewBox="0 0 867 1300">
    <image width="867" height="1300" xlink:href="../Images/200273.jpg"/>
</svg>
```

**结论**：核心修复 = 改动点 #1（Parser 识别 `image`）。这批书不依赖 SVG 解码器（内层是 jpg），但为完整性仍引入 coil-svg（改动点 #3–#5）。

## 5. 方案：coil-svg + 接通渲染链路

方案选型已确认（三选一，选 A）：
- **A. coil-svg（采用）**：复用现有 `AsyncImage` 链路；Android 用 androidsvg、桌面 JVM 用 Skiko 解码 SVG；跨平台一致。
- B. 正文改 WebView：推翻原生 Compose 渲染架构，否决。
- C. 自写 Skiko SVG 解码：两套代码、重复造轮子，否决。

### 5.1 改动点

| # | 文件 | 改动 | 层级 |
|---|---|---|---|
| 1 | [shared/build.gradle.kts:73](shared/build.gradle.kts#L73) | 加 `implementation("io.coil-kt.coil3:coil-svg:3.0.4")` | 配套 |
| 2 | commonMain 新增 `SingletonImageLoader` 配置 | 项目无全局 ImageLoader。新建一个，注册 `SvgDecoder.Factory()`，在应用入口 `SingletonImageLoader.setSafe { ... }` 调一次 | 配套 |
| 3 | [HtmlBlockRenderer.kt:114-136](shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockRenderer.kt#L114-L136) `BlockImage` | 删除 `isSvgDataUri` 跳过分支，统一走 `AsyncImage` | 配套 |
| 4 | [HtmlBlockParser.kt:45](shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/HtmlBlockParser.kt#L45) `collectBlocks` | **新增 `case "image"`**：取 `attr("xlink:href") ?: attr("href")` 作 `src`，产出 `HtmlBlock.Image` | **核心** |
| 5 | [ReaderViewModel.kt:224](shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt#L224) `svgImageRegex` | 现仅匹配 `xlink:href`；补裸 `href`（SVG2 写法）匹配 | 配套 |

### 5.2 改后数据流

```
EPUB
 → resolveImageReferences: svgImageRegex 匹配 xlink:href 与 href → data URI（位图 svg-wrapped / 真 SVG 均覆盖）
 → HtmlBlockParser: 识别 svg 内 <image> → HtmlBlock.Image(src=data:…)
 → BlockImage: 统一 AsyncImage（删 SVG 跳过）
 → Coil 解码: jpg/png/gif/webp 走默认解码器；image/svg+xml 走 SvgDecoder（Android=androidsvg / 桌面=Skiko）
 → 显示
```

### 5.3 关键实现细节

- **改动点 #4**：`collectBlocks` 的 `Element` 分支新增 `"image" -> HtmlBlock.Image(src = node.attr("xlink:href").ifBlank { node.attr("href") }, alt = node.attr("alt").takeIf { it.isNotBlank() })`。`<image>` 多在 `<svg>` 内，依靠现有对 `svg`（走 else 递归子节点）的递归处理自然命中。jsoup 保留带命名空间前缀的属性名 `xlink:href`，`attr("xlink:href")` 可直接取。
- **改动点 #2**：`SingletonImageLoader.setSafe` 幂等且线程安全，在 commonMain 应用根 composable 启动时调一次即可；具体挂载点（`App.kt` 顶层 / 各平台入口）在实现计划里定。
- **改动点 #5**：保持现有正则风格，扩展为同时匹配 `xlink:href` 与 `href`，注意与 `<img src>` 正则不冲突。

## 6. 错误处理与降级

- 复杂 SVG（`foreignObject`、外部资源引用、CSS 动画）androidsvg/Skiko 渲染不全时，**静默降级**：显示能渲染的部分或空白，不崩溃。优于当前完全不显示，可接受，不做额外兜底 UI。
- `<image>` 取不到任何 href 时：不产出块（跳过），不抛错。
- resolveImageReferences 解析失败（资源找不到）已有 `?: return null` 兜底，保持不变。

## 7. 测试策略

### 7.1 单元测试（TDD，先写）
`HtmlBlockParser` 是纯函数，易测，是核心新增逻辑的回归保障。用例：
- `<svg><image xlink:href="x.jpg"/></svg>` → 产出 1 个 `HtmlBlock.Image(src=x.jpg)`
- `<image href="x.png"/>`（裸 href）→ 产出 `HtmlBlock.Image`
- 用真实样本片段（§4 写法）作为测试输入。

### 7.2 集成验收（双端）
用 Mac 端真实书验收，无需造测试 EPUB：
- 主样本：`~/Library/SimpleBook/books/44884637-....epub`（18 张 svg-wrapped jpg，图片型 EPUB）。
- Android + 桌面各打开一本，确认页面图正常显示。
- 回归：打开纯文字 EPUB / 含普通 png jpg 插图的 EPUB，确认不回归。

### 7.3 验收标准
1. 打开 `44884637` 这类图片型 EPUB → 所有页面图正常显示（Android + 桌面）。
2. 原 png/jpeg 插图渲染不回归。
3. `HtmlBlockParser` 单元测试通过。

## 8. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| coil-svg 3.0.4 的 Skiko 版本与项目 CMP 不一致 | 桌面构建/运行冲突 | 构建期对齐版本；必要时升级 coil 至 3.x 较新版本（需同步评估兼容性） |
| 图片型 EPUB 全页图 aspect ratio | `fillMaxWidth` 显示，AsyncImage 默认 `ContentScale.Fit` 保留比例，应正常；极宽/极高图可能需滚动 | 先按现状验收，如有比例问题再单独处理（不纳入本次范围） |
| 复杂 SVG 渲染不全 | 个别矢量图降级 | §6 静默降级，可接受 |

## 9. 改动文件清单

- `shared/build.gradle.kts`（+1 依赖）
- `shared/src/commonMain/.../ui/reader/HtmlBlockParser.kt`（核心：+case image）
- `shared/src/commonMain/.../ui/reader/HtmlBlockRenderer.kt`（删 SVG 跳过）
- `shared/src/commonMain/.../ui/reader/ReaderViewModel.kt`（href 正则补全）
- commonMain 新增 ImageLoader 配置文件（注册 SvgDecoder）
- 新增 `HtmlBlockParser` 单元测试

## 10. 不在本次范围

- 内联矢量 SVG（`<svg>` 直接画矢量）的渲染
- EPUB SVG 封面页之外的复杂 SVG 排版（foreignObject 等）
- 阅读器整体渲染引擎改 WebView
- 品牌位图资源改 SVG、图标体系迁移（方向 B/C，独立话题）
