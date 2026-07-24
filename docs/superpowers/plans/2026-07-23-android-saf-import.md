# Android SAF 导入接线 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 接通 Android 端书架 SpeedDial「导入书籍」按钮——点击弹出系统文件选择器（多选 EPUB/TXT），导入后出现在书架可阅读。

**架构：** 仿现有 `signInLauncher: (() -> Unit)?` 模式——SAF 启动器在 `MainActivity`（commonMain 无法引用 Android ActivityResult API），把「触发 lambda」经 `SimpleBookNavHost` 下发到 `BookListScreen` 的 SpeedDial 按钮；选中文件经 `List<Uri>.toImportSources(context)` → `BookListViewModel.importSources()` → `FileImportService`（已就绪）。桌面端 `onImportClick == null` 时隐藏该按钮。

**技术栈：** Kotlin Multiplatform（commonMain + androidMain + desktopMain）、Jetpack Compose、`androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments`、Koin。

**规格：** [docs/superpowers/specs/2026-07-23-android-saf-import-design.md](../specs/2026-07-23-android-saf-import-design.md)

---

## 构建环境

如未设 `JAVA_HOME`，所有 `./gradlew` 命令前加：
```bash
JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```
（用户环境通常已设；本机 Android SDK 在 `~/Library/Android/sdk`。）

## 验证命令对照

- Android 编译/打包：`./gradlew :androidApp:assembleDebug`
- commonMain 桌面侧编译回归（同时跑既有测试）：`./gradlew :shared:desktopTest`

> **关于测试：** 本计划全部为 Activity/Composable 接线胶水代码，无新增可隔离的纯逻辑；SAF 行为依赖 Android 运行时无法单测。故每个任务以「编译通过 + 既有测试不回归」为自动门禁，端到端验证集中在任务 5 手动进行。底层 building blocks（`AndroidUriSource`、`toImportSources`、`FileImportService`）均已实现，不在本计划范围。

## 文件结构

| 文件 | 改动 | 职责 |
|------|------|------|
| `shared/src/commonMain/.../ui/booklist/BookListScreen.kt` | 修改 | 加 `onImportClick` 回调；SpeedDial「导入书籍」项按回调是否存在显示/隐藏并触发；清理过期注释 |
| `shared/src/commonMain/.../ui/navigation/SimpleBookNavHost.kt` | 修改 | 加 `onImportClick` 参数透传给 `BookListScreen` |
| `androidApp/src/main/.../MainActivity.kt` | 修改 | 建 `OpenMultipleDocuments` 启动器；回调转 `ImportSource` 并喂 `bookListViewModel`；把触发 lambda 传入 NavHost |
| `shared/src/androidMain/.../domain/service/ImportIntents.kt` | 删除 | `createImportIntent()` 改用 `OpenMultipleDocuments` 后成死代码 |

依赖关系：Task 1 给 `BookListScreen` 加带默认值的参数 → 独立可编译；Task 2 给 NavHost 加带默认值的参数 → 独立可编译；Task 3 在 MainActivity 接真实启动器（功能在此步生效）；Task 4 清理死代码。每个任务的提交都能独立编译。

---

## 任务 1：BookListScreen — 接入 onImportClick 回调

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/booklist/BookListScreen.kt`

- [ ] **步骤 1：删除顶部过期注释 import（第 3-6 行）**

把文件开头的：
```kotlin
package com.ebookreader.simplebook.ui.booklist

// TODO: Desktop compatibility - re-enable file import when supported
// import android.net.Uri
// import androidx.activity.compose.rememberLauncherForActivityResult
// import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
```
改为：
```kotlin
package com.ebookreader.simplebook.ui.booklist

import androidx.compose.foundation.clickable
```

- [ ] **步骤 2：函数签名增加 `onImportClick` 参数（第 77-79 行附近）**

把：
```kotlin
    onSyncClick: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
```
改为：
```kotlin
    onSyncClick: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onImportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
```

- [ ] **步骤 3：更新 SAF NOTE 注释（第 107-112 行）**

把：
```kotlin
        // NOTE (Android SAF): BookListScreen lives in commonMain and cannot host an Android
        // ActivityResultContracts launcher directly. The SpeedDial "Import Books" button is
        // therefore not wired on Android yet — drag-and-drop is the desktop path. To restore
        // Android SAF, thread an `onImportClick` callback up to MainActivity, launch
        // `createImportIntent()` there, and feed the returned Uris through
        // `List<Uri>.toImportSources(context)` -> `viewModel.importSources(...)`.
    Scaffold(
```
改为：
```kotlin
        // Android SAF: the launcher can't live in commonMain, so MainActivity owns it and
        // passes an `onImportClick` trigger through SimpleBookNavHost. When null (desktop),
        // the "Import Books" SpeedDial item is hidden — desktop imports via drag-and-drop.
    Scaffold(
```

- [ ] **步骤 4：SpeedDial items 改为 listOfNotNull，按回调是否存在显示「导入书籍」项（第 199-225 行）**

把：
```kotlin
                items = listOf(
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = strings.importBooks,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            // Desktop: import via drag-and-drop onto the screen (DragDropOverlay).
                            // Android: SAF launcher wiring is a TODO (see NOTE above).
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        label = strings.newFolder,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            showNewFolderDialog = true
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        label = if (settings.sortOrder == SortOrder.LAST_READ) strings.sortByLastRead else strings.sortByName,
                        onClick = {
                            val next = if (settings.sortOrder == SortOrder.LAST_READ) SortOrder.NAME else SortOrder.LAST_READ
                            viewModel.updateSortOrder(next)
                        }
                    )
                ),
```
改为：
```kotlin
                items = listOfNotNull(
                    onImportClick?.let { cb ->
                        SpeedDialItem(
                            icon = { Icon(Icons.Default.Add, contentDescription = null) },
                            label = strings.importBooks,
                            onClick = {
                                viewModel.dismissSpeedDial()
                                cb()
                            }
                        )
                    },
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        label = strings.newFolder,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            showNewFolderDialog = true
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        label = if (settings.sortOrder == SortOrder.LAST_READ) strings.sortByLastRead else strings.sortByName,
                        onClick = {
                            val next = if (settings.sortOrder == SortOrder.LAST_READ) SortOrder.NAME else SortOrder.LAST_READ
                            viewModel.updateSortOrder(next)
                        }
                    )
                ),
```
> `onImportClick?.let { cb -> ... }` 在 `onImportClick == null`（桌面）时返回 `null`，被 `listOfNotNull` 过滤掉 → 桌面不显示「导入书籍」项；`cb` 在 let 块内智能转换为非空。

- [ ] **步骤 5：Android 编译验证**

运行：`./gradlew :androidApp:assembleDebug`
预期：`BUILD SUCCESSFUL`（commonMain 改动同时被 android target 编译）。

- [ ] **步骤 6：桌面侧编译 + 既有测试回归**

运行：`./gradlew :shared:desktopTest`
预期：`BUILD SUCCESSFUL`，既有测试全过（确认 commonMain 改动未破坏桌面 target）。注意：本步后桌面端 SpeedDial 不再显示「导入书籍」项（onImportClick 默认 null）——这是预期行为。

- [ ] **步骤 7：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/booklist/BookListScreen.kt
git commit -m "feat(booklist): 接入 onImportClick 回调，桌面端隐藏导入项"
```

---

## 任务 2：SimpleBookNavHost — 透传 onImportClick

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt`

- [ ] **步骤 1：函数签名增加 `onImportClick` 参数（第 27-29 行）**

把：
```kotlin
    syncViewModel: SyncViewModel? = null,
    signInLauncher: (() -> Unit)? = null,
    modifier: Modifier = Modifier
```
改为：
```kotlin
    syncViewModel: SyncViewModel? = null,
    signInLauncher: (() -> Unit)? = null,
    onImportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
```

- [ ] **步骤 2：构造 BookListScreen 时透传（第 49-60 行）**

把：
```kotlin
            BookListScreen(
                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                syncViewModel = svm,
                onBookClick = { book: Book ->
                    navController.navigate(Screen.Reader.createRoute(book.uuid))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
```
改为：
```kotlin
            BookListScreen(
                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                syncViewModel = svm,
                onBookClick = { book: Book ->
                    navController.navigate(Screen.Reader.createRoute(book.uuid))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onImportClick = onImportClick
            )
```

- [ ] **步骤 3：编译验证（两端）**

运行：`./gradlew :androidApp:assembleDebug :shared:desktopTest`
预期：`BUILD SUCCESSFUL`，既有测试全过。

- [ ] **步骤 4：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt
git commit -m "feat(nav): SimpleBookNavHost 透传 onImportClick 到 BookListScreen"
```

---

## 任务 3：MainActivity — 接通 SAF 启动器（功能生效）

**文件：**
- 修改：`androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt`

- [ ] **步骤 1：新增 import**

在文件 import 区按分组添加两行：
- `androidx.compose.ui.*` 组（`import androidx.compose.ui.Alignment` 附近）加：
  ```kotlin
  import androidx.compose.ui.platform.LocalContext
  ```
- `com.ebookreader.simplebook.*` 组（`import com.ebookreader.simplebook.domain.model.getStrings` 附近）加：
  ```kotlin
  import com.ebookreader.simplebook.domain.service.toImportSources
  ```

> 说明：回调里 `uris` 类型由 `OpenMultipleDocuments` 推断为 `List<Uri>`，调用扩展函数 `toImportSources` 即可，无需显式 `import android.net.Uri`。

- [ ] **步骤 2：新增 SAF 启动器（在 `googleSignInLauncher` 块之后、`val lifecycleOwner` 之前，约第 145-147 行之间插入）**

在：
```kotlin
                        } else {
                            syncViewModel.setSignInError("登录已取消 (code=${result.resultCode})")
                        }
                        }

                        val lifecycleOwner = LocalLifecycleOwner.current
```
的中间（两个 `}` 与 `val lifecycleOwner` 之间）插入：
```kotlin

                        // Android SAF import: OpenMultipleDocuments returns the picked Uris
                        // directly. Convert to ImportSource list and feed the shared
                        // Activity-scoped BookListViewModel (same instance used for the splash
                        // gate, declared at the SimpleBookTheme scope above). The shelf list
                        // refreshes via Room Flow once books land in the DB.
                        val context = LocalContext.current
                        val importLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.OpenMultipleDocuments()
                        ) { uris ->
                            if (uris.isNotEmpty()) {
                                bookListViewModel.importSources(uris.toImportSources(context))
                            }
                        }
```

> `bookListViewModel` 即第 79 行 `koinViewModel()`（Activity 级），在此内层作用域可直接捕获。

- [ ] **步骤 3：把触发 lambda 传入 SimpleBookNavHost（第 167-172 行）**

把：
```kotlin
                            SimpleBookNavHost(
                                navController = navController,
                                windowSizeClass = windowSizeClass,
                                syncViewModel = syncViewModel,
                                signInLauncher = { googleSignInLauncher.launch(authManager.signInIntent) }
                            )
```
改为：
```kotlin
                            SimpleBookNavHost(
                                navController = navController,
                                windowSizeClass = windowSizeClass,
                                syncViewModel = syncViewModel,
                                signInLauncher = { googleSignInLauncher.launch(authManager.signInIntent) },
                                onImportClick = {
                                    importLauncher.launch(arrayOf("application/epub+zip", "text/plain"))
                                }
                            )
```

- [ ] **步骤 4：Android 编译验证**

运行：`./gradlew :androidApp:assembleDebug`
预期：`BUILD SUCCESSFUL`。若报 `Unresolved reference: toImportSources` / `LocalContext`，检查步骤 1 的 import 是否加对；若报 `importLauncher` 未解析，检查步骤 2 的插入位置是否在 `Surface { ... }` 块内、NavHost 调用之前。

- [ ] **步骤 5：Commit**

```bash
git add androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt
git commit -m "feat(android): 接通 SAF 导入启动器（OpenMultipleDocuments）"
```

---

## 任务 4：删除未使用的 createImportIntent

**文件：**
- 删除：`shared/src/androidMain/kotlin/com/ebookreader/simplebook/domain/service/ImportIntents.kt`

- [ ] **步骤 1：先读文件确认其只含 createImportIntent（无其他声明）**

运行：`cat shared/src/androidMain/kotlin/com/ebookreader/simplebook/domain/service/ImportIntents.kt`
预期：仅 `package` + `import`（Intent）+ `fun createImportIntent(): Intent`，无其他顶层声明。

- [ ] **步骤 2：确认无残留引用**

运行：`grep -rn "createImportIntent" shared/ androidApp/ desktopApp/`
预期：**无输出**（Task 1 已删掉引用它的 NOTE 注释；旧 SAF 调用点早已不存在）。若有输出，先清理引用再删文件。

- [ ] **步骤 3：删除文件**

```bash
git rm shared/src/androidMain/kotlin/com/ebookreader/simplebook/domain/service/ImportIntents.kt
```

- [ ] **步骤 4：编译验证**

运行：`./gradlew :androidApp:assembleDebug`
预期：`BUILD SUCCESSFUL`（androidMain 文件删除后无引用残留）。

- [ ] **步骤 5：Commit**

```bash
git commit -m "chore(android): 移除未使用的 createImportIntent"
```

---

## 任务 5：端到端手动验证（Android 真机/模拟器）

**文件：** 无（纯验证，不产生提交）

- [ ] **步骤 1：打包并安装**

```bash
./gradlew :androidApp:assembleDebug
adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/app-debug.apk
```
（模拟器 ID 按实际替换；真机用 `adb -s <serial>`。SAF 导入纯本地，不碰登录/同步网络链路，模拟器即可。）

- [ ] **步骤 2：正常导入（多选 EPUB + TXT）**

打开 app → 书架 → 点 SpeedDial「导入书籍」→ 系统文件选择器弹出 → 多选 1 本 EPUB + 1 本 TXT（从 Downloads/Files）→ 确认。
预期：两本书出现在书架。

- [ ] **步骤 3：打开阅读**

点开任一刚导入的书。
预期：进入阅读界面，可翻页/滚动。

- [ ] **步骤 4：取消选择不崩**

再点「导入书籍」→ 系统选择器里按返回/取消。
预期：选择器关闭，书架不变，无崩溃。

- [ ] **步骤 5：不支持类型被跳过**

点「导入书籍」→ 选一个非 epub/txt 文件（若选择器因 MIME 过滤不显示，则本步跳过即可）→ 确认。
预期：不崩溃；该文件被 `FileImportService` 按扩展名跳过。

- [ ] **步骤 6：桌面端回归**

运行：`./gradlew :desktopApp:run`
预期：书架 SpeedDial **不再显示**「导入书籍」项（仅「新建文件夹」「排序」）；拖放 EPUB/TXT 到窗口仍可导入。

- [ ] **步骤 7：全量回归测试**

运行：`./gradlew :shared:desktopTest`
预期：既有测试全过。

---

## 自检结果

**1. 规格覆盖度：** 逐条对照规格第 3 节文件改动清单——BookListScreen（onImportClick 参数 + 接线 + 隐藏 + 清理 NOTE/注释）= 任务 1；SimpleBookNavHost 透传 = 任务 2；MainActivity 启动器 = 任务 3；删除 createImportIntent = 任务 4；规格第 6 节测试与验证、第 8 节验收标准 = 任务 5。规格第 5 节错误处理经核实全部「既有」（FileImportService 逐文件 try-catch 已确认），无需新增任务。✅ 无遗漏。

**2. 占位符扫描：** 无 TODO/待定/「类似任务 N」；每个代码步骤均含完整 old/new 代码块；命令含预期输出。✅

**3. 类型一致性：** `onImportClick: (() -> Unit)?` 在 BookListScreen（任务 1）、NavHost（任务 2）、MainActivity 传入处（任务 3）三处签名一致；`importSources(List<ImportSource>)` 调用与既有 `BookListViewModel`/`FileImportService` 一致；`toImportSources(context)` 与既有扩展一致；MIME 数组 `["application/epub+zip", "text/plain"]` 与原 `createImportIntent` 配置一致。✅
