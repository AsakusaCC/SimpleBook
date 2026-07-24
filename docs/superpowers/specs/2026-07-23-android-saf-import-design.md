# SimpleBook Android SAF 导入接线设计文档

> 日期: 2026-07-23
> 状态: 已批准
> 分支: feat/kmp-desktop-migration

## 1. 背景与问题

### 1.1 现象

KMP 迁移后，Android 端书架 SpeedDial 的「导入书籍」按钮点击无反应——只收起菜单，不弹出系统文件选择器。Android 端目前没有任何本地导入入口（桌面端已通过拖放导入可用）。

### 1.2 根因

原单模块 Android 版的 SAF 导入在迁移到 `commonMain` 时丢失。`BookListScreen` 位于 `commonMain`，无法直接挂 Android 的 `ActivityResultContracts` 启动器，于是 SpeedDial 的导入项 `onClick` 被降级为只调 `dismissSpeedDial()`，并留了 TODO 注释。

代码核查确认：**导入链路的所有底层 building blocks 均已实现且非 stub**，缺的只是「按钮 → MainActivity 启动器 → ViewModel」这根线。

已就绪的 building blocks：

| 组件 | 位置 | 状态 |
|------|------|------|
| `createImportIntent()` | `shared/androidMain/.../domain/service/ImportIntents.kt` | 已实现 |
| `List<Uri>.toImportSources(context)` | `shared/androidMain/.../domain/service/AndroidUriSource.kt` | 已实现 |
| `ImportSource` / `AndroidUriSource` / `DesktopFileSource` | `shared/commonMain` + `androidMain` | 已实现 |
| `FileImportService.importFromSources(List<ImportSource>)` | `shared/commonMain/.../domain/service/FileImportService.kt` | 已实现 |
| `BookListViewModel.importSources(List<ImportSource>)` | `shared/commonMain/.../ui/booklist/BookListViewModel.kt` | 已实现 |

### 1.3 目标

接通 Android 端 SAF 导入：点「导入书籍」→ 系统文件选择器（多选 EPUB/TXT）→ 选中文件导入沙箱 → 出现在书架并可阅读。纯本地链路，不依赖登录/同步。

## 2. 核心决策

| 维度 | 决策 | 理由 |
|------|------|------|
| 接线模式 | 仿 `signInLauncher: (() -> Unit)?`——启动器在 MainActivity，触发 lambda 经 NavHost 下发到按钮 | 复用已验证的同构先例；commonMain 不引用 Android Activity API |
| ActivityResult Contract | `ActivityResultContracts.OpenMultipleDocuments()` | 直接返回 `List<Uri>`，无需手动解析 `ClipData`；比 `StartActivityForResult + createImportIntent()` 更简洁 |
| MIME 过滤 | `arrayOf("application/epub+zip", "text/plain")` 传入 launch() | 与 `createImportIntent()` 原配置一致 |
| ViewModel 实例 | 回调直接捕获 MainActivity 已在作用域的 `bookListViewModel`（第 79 行，Activity 级） | 不在回调里 `KoinPlatform.get()` 另取实例；与书架屏（NavBackStackEntry 级）虽是两个实例，但导入写 Room 后书架靠 Flow 自动刷新，功能正确 |
| 桌面端按钮 | `onImportClick == null` 时**隐藏**「导入书籍」SpeedDial 项 | 桌面走拖放，避免死按钮 |
| 清理 | 改用 OpenMultipleDocuments 后 `createImportIntent()` 变死代码，删除 | 避免遗留死代码 |

## 3. 文件改动清单

### 3.1 `shared/src/commonMain/.../ui/booklist/BookListScreen.kt`

1. 函数签名增加参数：`onImportClick: (() -> Unit)? = null`（与现有 `onSyncClick`/`onNavigateToSettings` 同为可空回调）。
2. SpeedDial「导入书籍」项 `onClick` 改为：
   ```kotlin
   onClick = {
       viewModel.dismissSpeedDial()
       onImportClick?.invoke()
   }
   ```
3. SpeedDial items 列表：当 `onImportClick == null` 时，过滤掉「导入书籍」项（不在桌面显示）。
4. 删除/更新原 SAF TODO NOTE 注释（任务完成后不再 TODO）。

### 3.2 `shared/src/commonMain/.../ui/navigation/SimpleBookNavHost.kt`

1. 函数签名增加参数：`onImportClick: (() -> Unit)? = null`。
2. 在 `composable(Screen.BookList.route) { BookListScreen(...) }` 处透传：`onImportClick = onImportClick`。

### 3.3 `androidApp/src/main/kotlin/.../MainActivity.kt`

在 `googleSignInLauncher` 同级（`setContent` 内，`bookListViewModel` 已在作用域内）新增：

```kotlin
val context = LocalContext.current
val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenMultipleDocuments()
) { uris ->
    if (uris.isNotEmpty()) {
        bookListViewModel.importSources(uris.toImportSources(context))
    }
}
```

并在构造 `SimpleBookNavHost(...)` 时传入：

```kotlin
onImportClick = {
    importLauncher.launch(arrayOf("application/epub+zip", "text/plain"))
}
```

> 说明：`bookListViewModel` 即 MainActivity 第 79 行 `koinViewModel()`（Activity 级，与 `syncViewModel` 同级）。回调与书架屏共用此实例——但当前 `BookListScreen` 经 NavHost 时用的是 NavBackStackEntry 级实例，二者不同。导入写库后书架列表靠 Room Flow 自动刷新，故功能正确；若后续发现导入后书架不刷新，再考虑把 Activity 级 `bookListViewModel` 经 NavHost 下发统一实例（与 syncViewModel 同修法）。本次按最小改动，不主动改 BookListScreen 的 VM 来源。

### 3.4 `shared/src/androidMain/.../domain/service/ImportIntents.kt`

删除整个文件（`createImportIntent()` 及其 import 不再被引用）。

## 4. 接线数据流

```
[Android] BookListScreen SpeedDial「导入书籍」onClick
   │  onImportClick?.invoke()
   ▼
SimpleBookNavHost 透传 onImportClick lambda
   │
   ▼
MainActivity: importLauncher.launch(arrayOf(epub, txt))
   │  系统弹出 SAF 文件选择器（多选）
   ▼
用户选中 N 个文件 → 回调 List<Uri>
   │  uris.toImportSources(context)
   ▼
List<ImportSource>  (AndroidUriSource 包装每个 Uri)
   │  bookListViewModel.importSources(sources)
   ▼
FileImportService.importFromSources(sources)
   │  逐个 openInputStream() → 拷进沙箱 books/ → bookService.importBook()
   ▼
Room 写库 → BookListViewModel 的 books Flow 发射 → 书架刷新

[Desktop] onImportClick == null → 「导入书籍」项隐藏 → 拖放导入（DragDropOverlay）不受影响
```

## 5. 错误处理与边界

| 场景 | 处理 | 既有/新增 |
|------|------|-----------|
| 用户取消选择 | 回调返回空列表，`importSources` 已 `if (sources.isEmpty()) return` 短路 | 既有 |
| 选了不支持的扩展名 | `FileImportService` 按 `SUPPORTED_EXTENSIONS = {epub, txt}` 跳过 | 既有 |
| 损坏/截断 EPUB | `EpubParser.parse` 返回 null 守卫，`BookService` 抛可控异常被 `FileImportService` 捕获 | 既有（commit `1b5c98b`） |
| 单个 Uri 无法打开 | `AndroidUriSource.openInputStream()` 抛 `FileNotFoundException`，被 `importFromSources` 的逐文件 `try/catch` 捕获并跳过 | 既有（[FileImportService.kt:26-39](../../../shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/FileImportService.kt) 逐文件隔离，单文件失败不中断整批） |
| 运行时权限 | `ACTION_OPEN_DOCUMENT`（经 `OpenMultipleDocuments`）无需任何存储权限；文件立即拷进沙箱，不需 `takePersistableUriPermission` | 无需新增 |

## 6. 测试与验证

**单元测试**：无新增必要。本次改动均为 Activity/Composable 层接线胶水代码，底层 building blocks（`AndroidUriSource`、`toImportSources`、`FileImportService`）已实现，且 SAF 行为依赖 Android 运行时，难以单测。

**手动验证（必做）**：

1. 构建：`./gradlew :androidApp:assembleDebug`（需 `androidApp/google-services.json`）。
2. 安装：`adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/app-debug.apk`（或真机）。
3. 打开 app → 书架 → 点 SpeedDial「导入书籍」→ 弹出系统文件选择器。
4. 多选 1 本 EPUB + 1 本 TXT（从 Downloads/Files）→ 确认 → 两本出现在书架。
5. 打开任一本 → 能进入阅读、翻页/滚动。
6. 取消选择 → 不崩、书架不变。
7. 选一个非 epub/txt 文件（若选择器允许）→ 被跳过，不崩。
8. 桌面端回归：`./gradlew :desktopApp:run` → 书架 SpeedDial 不再显示「导入书籍」项；拖放导入仍正常。

**验证说明**：SAF 导入纯本地，不碰登录/同步网络链路（模拟器登录有 NETWORK_ERROR 环境问题，与本任务无关）。

## 7. 范围边界（本次不做）

- 桌面端「导入书籍」按钮接原生文件选择器对话框（桌面仍仅拖放）。
- 导入进度 UI / loading 态（当前 `importSources` fire-and-forget，无进度展示）。
- 导入结果反馈（成功/失败 toast 或 snackbar）。
- `FileImportService` 单文件异常隔离 / IO 线程调度（`viewModelScope` 默认 Main，文件拷贝可能阻塞——既有问题，独立处理）。
- 把 Activity 级 `bookListViewModel` 经 NavHost 下发统一实例（仅在导入后书架不刷新时才做）。
- access_token refresh、reauth 流程恢复（独立同步遗留项）。

## 8. 验收标准

1. `./gradlew :androidApp:assembleDebug` 编译通过。
2. Android 端点「导入书籍」弹出系统文件选择器，可多选 EPUB/TXT。
3. 选中的书导入后出现在书架，可正常打开阅读。
4. 取消选择不崩；不支持类型被跳过不崩。
5. 桌面端书架 SpeedDial 不显示「导入书籍」项，拖放导入功能不回归。
6. `createImportIntent()` 死代码已删除，无残留引用。
