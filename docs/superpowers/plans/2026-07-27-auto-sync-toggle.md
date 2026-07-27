# 同步设置（自动/手动同步开关）实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在设置页「Google Drive 同步」与「从 Drive 导入」之间新增「同步设置」分区，内含一个 Switch 开关；关闭后应用进入前台（ON_START）不再自动同步，只有用户主动点「立即同步」时才同步，解决息屏/亮屏频繁触发同步的问题。

**架构：** 扩展 `SyncPreferences`（同步专用偏好）加 boolean 支持，将其纳入 Koin 单例并构造注入到 `SyncService` / `SyncViewModel`。`SyncViewModel` 暴露 `autoSyncEnabled` StateFlow 与 `toggleAutoSync()` / `autoSyncIfEnabled()`。两处 ON_START 生命周期监听改调 `autoSyncIfEnabled()`，从而受开关控制。状态持久化到 `sync_prefs`（Android SharedPreferences / Desktop Properties）。

**技术栈：** Kotlin Multiplatform、Compose Multiplatform、Koin、material3.Switch、kotlin.test（desktopTest）。

**规格：** [docs/superpowers/specs/2026-07-27-auto-sync-toggle-design.md](../specs/2026-07-27-auto-sync-toggle-design.md)

---

## 关于测试策略的说明（对规格 5.7 的调整）

规格 5.7 设想了「mock `SyncService` + `SyncPreferences` 的 `SyncViewModel` 单测」。经核实工程现状后不可行，原因：

- 工程只有 `desktopTest` 一个测试源集，框架为 `kotlin("test")`，**无任何 mock 库**（mockk / mockito 均未引入）。
- `SyncService` 是 **final 具体类，构造函数有 12 个依赖**（GoogleDriveClient、6 个 Repository、SyncLogDao、Gson、两个 Parser 等），且多为具体类/expect 类，无法手写 fake，不引入 mock 库就无法实例化。
- ON_START 门控逻辑位于 `App.kt` / `MainActivity.kt` 的 Composable `DisposableEffect` 中，无法单测。

因此本计划的测试调整为：

1. **有真实单测（TDD）**：`SyncPreferences` 的 boolean 持久化往返（desktopTest）。这是本功能唯一非平凡且可单测的新逻辑。为支持隔离测试，给桌面端 `SyncPreferences` 增加一个 `internal constructor(file: File)` 测试缝隙——与现有 [TokenStorageTest.kt](../../../shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/TokenStorageTest.kt) 注入 `File` 的模式完全一致。
2. **手动验证**：开关 UI、状态跨重启持久化、ON_START 门控、手动「立即同步」不受影响——见任务 8 的清单。

（Android 端无 `androidTest` 源集，故 Android 的 SharedPreferences boolean 分支由「与桌面同一存储模式 + 桌面往返测试 + 手动验证」覆盖。）

---

## 文件结构

| 文件 | 职责 | 操作 |
|---|---|---|
| `shared/src/commonMain/.../platform/SyncPreferences.kt` | 同步偏好 expect 声明 | 修改：加 `getBoolean/putBoolean` |
| `shared/src/androidMain/.../platform/SyncPreferences.kt` | Android SharedPreferences 实现 | 修改：加 `getBoolean/putBoolean` |
| `shared/src/desktopMain/.../platform/SyncPreferences.kt` | Desktop Properties 实现 | 修改：加 `internal constructor(file)` 测试缝隙 + `getBoolean/putBoolean` |
| `shared/src/desktopTest/.../platform/SyncPreferencesTest.kt` | boolean 持久化往返测试 | 创建 |
| `shared/src/androidMain/.../di/PlatformModule.kt` | Android Koin 模块 | 修改：注册 `SyncPreferences` single |
| `shared/src/desktopMain/.../di/PlatformModule.kt` | Desktop Koin 模块 | 修改：注册 `SyncPreferences` single |
| `shared/src/commonMain/.../domain/service/SyncService.kt` | 同步服务 | 修改：`prefs` 改构造注入 |
| `shared/src/commonMain/.../ui/sync/SyncViewModel.kt` | 同步 VM | 修改：加 `autoSyncEnabled`/`toggleAutoSync()`/`autoSyncIfEnabled()` + 注入 prefs |
| `shared/src/commonMain/.../App.kt` | 共用入口生命周期监听 | 修改：ON_START 改调 `autoSyncIfEnabled()` |
| `androidApp/src/main/.../MainActivity.kt` | Android 入口生命周期监听 | 修改：ON_START 改调 `autoSyncIfEnabled()` |
| `shared/src/commonMain/.../domain/model/AppStrings.kt` | 文案 | 修改：加三字段 + 中英文 |
| `shared/src/commonMain/.../ui/settings/SettingsScreen.kt` | 设置页 UI | 修改：新增「同步设置」分区 + Switch |

---

## 任务 1：`SyncPreferences` boolean 支持 + 桌面测试缝隙（TDD）

**文件：**
- 创建（测试）：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/SyncPreferencesTest.kt`
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt`
- 修改：`shared/src/androidMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt`
- 修改：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt`

- [ ] **步骤 1：编写失败的测试**

创建 `shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/SyncPreferencesTest.kt`：

```kotlin
package com.ebookreader.simplebook.platform

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPreferencesTest {

    private val tempDir = Files.createTempDirectory("syncprefs-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newPrefs() = SyncPreferences(java.io.File(tempDir, "sync_prefs.properties"))

    @Test
    fun getBoolean_returnsDefault_whenKeyAbsent() {
        val prefs = newPrefs()
        // 键不存在 → 返回传入的 default
        assertFalse(prefs.getBoolean("auto_sync", false))
        assertTrue(prefs.getBoolean("auto_sync", true))
    }

    @Test
    fun putBoolean_roundTripsTrueAndFalse() {
        val prefs = newPrefs()
        prefs.putBoolean("auto_sync", true)
        assertTrue(prefs.getBoolean("auto_sync", false))
        prefs.putBoolean("auto_sync", false)
        assertFalse(prefs.getBoolean("auto_sync", true))
    }

    @Test
    fun putBoolean_persistsAcrossInstances_onSameFile() {
        // 证明 putBoolean 真的落盘并能被新实例重新加载（save + load 往返）
        val file = java.io.File(tempDir, "sync_prefs.properties")
        SyncPreferences(file).putBoolean("auto_sync", true)
        val reloaded = SyncPreferences(file)
        assertTrue(reloaded.getBoolean("auto_sync", false))
    }
}
```

> 测试通过 `SyncPreferences(file)` 的 `internal constructor(file: File)`（桌面端独有）注入临时文件，避免污染真实 `~/Library/SimpleBook/sync_prefs.properties`。

- [ ] **步骤 2：运行测试验证失败（RED）**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.platform.SyncPreferencesTest"`
预期：编译失败 —— `getBoolean` / `putBoolean` 与 `internal constructor(file: File)` 均未定义（unresolved reference）。

- [ ] **步骤 3：commonMain expect 增加 boolean 声明**

将 `shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt` 改为：

```kotlin
package com.ebookreader.simplebook.platform

expect class SyncPreferences() {
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getStringSet(key: String, default: Set<String>? = null): Set<String>?
    fun putStringSet(key: String, value: Set<String>)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
}
```

- [ ] **步骤 4：Android actual 增加 boolean 实现**

将 `shared/src/androidMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt` 改为（在 `putStringSet` 之后追加两个 `actual fun`）：

```kotlin
package com.ebookreader.simplebook.platform

import android.content.Context
import org.koin.mp.KoinPlatform

actual class SyncPreferences actual constructor() {
    private val prefs by lazy {
        KoinPlatform.getKoin().get<Context>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    }

    actual fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    actual fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    actual fun getStringSet(key: String, default: Set<String>?): Set<String>? = prefs.getStringSet(key, default)
    actual fun putStringSet(key: String, value: Set<String>) =
        prefs.edit().putStringSet(key, value).apply()
    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()
}
```

- [ ] **步骤 5：Desktop actual 增加测试缝隙 + boolean 实现**

将 `shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt` 整体替换为：

```kotlin
package com.ebookreader.simplebook.platform

import java.io.File
import java.util.Properties

actual class SyncPreferences {
    private val file: File
    private val props: Properties

    actual constructor() : this(
        File(System.getProperty("user.home"), "Library/SimpleBook/sync_prefs.properties")
    )

    // 测试缝隙：注入任意文件，避免污染真实 home 目录（同 TokenStorage 的可测性模式）
    internal constructor(file: File) {
        this.file = file
        this.props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    }

    actual fun getLong(key: String, default: Long): Long =
        props.getProperty(key)?.toLongOrNull() ?: default

    actual fun putLong(key: String, value: Long) {
        props[key] = value.toString()
        save()
    }

    actual fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        props.getProperty(key)?.split(",")?.toSet() ?: default

    actual fun putStringSet(key: String, value: Set<String>) {
        props[key] = value.joinToString(",")
        save()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBoolean() ?: default

    actual fun putBoolean(key: String, value: Boolean) {
        props[key] = value.toString()
        save()
    }

    private fun save() {
        file.parentFile.mkdirs()
        // Non-sensitive data (sync timestamps, import-id cache) — no owner-only
        // hardening here, unlike TokenStorage which holds OAuth tokens.
        file.outputStream().use { props.store(it, null) }
    }
}
```

> 说明：原 `actual class ... actual constructor() { ... }` 改为无主构造的类 + 两个次构造。`actual constructor()`（无参，对外/Koin）委托给 `internal constructor(file)`。`file` / `props` 在每个次构造中赋值，满足明确赋值要求。所有原有方法签名不变，行为等价。

- [ ] **步骤 6：运行测试验证通过（GREEN）**

运行：`./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.platform.SyncPreferencesTest"`
预期：3 个测试全部 PASS。

- [ ] **步骤 7：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt \
        shared/src/androidMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt \
        shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/SyncPreferences.kt \
        shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/SyncPreferencesTest.kt
git commit -m "feat: SyncPreferences 支持 boolean 读写并加桌面测试缝隙"
```

---

## 任务 2：`SyncPreferences` 纳入 Koin 单例

**文件：**
- 修改：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt`
- 修改：`shared/src/androidMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt`

- [ ] **步骤 1：Desktop PlatformModule 注册**

在 `shared/src/desktopMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt`：

新增 import（与现有 `SettingsDataStore` import 放一起）：

```kotlin
import com.ebookreader.simplebook.platform.SyncPreferences
```

在 `singleOf(::SettingsDataStore)` 下一行追加：

```kotlin
    singleOf(::SyncPreferences)
```

- [ ] **步骤 2：Android PlatformModule 注册**

在 `shared/src/androidMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt`：

新增 import：

```kotlin
import com.ebookreader.simplebook.platform.SyncPreferences
```

在 `singleOf(::SettingsDataStore)` 下一行追加：

```kotlin
    singleOf(::SyncPreferences)
```

- [ ] **步骤 3：验证编译**

运行：`./gradlew :shared:desktopTest`
预期：编译通过、桌面测试仍全绿（Koin 注册不影响测试，但能验证 commonMain + desktopMain 仍可编译）。

- [ ] **步骤 4：Commit**

```bash
git add shared/src/desktopMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt \
        shared/src/androidMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt
git commit -m "refactor: SyncPreferences 纳入 Koin 单例（两平台 PlatformModule）"
```

---

## 任务 3：`SyncService` 改用构造注入 `SyncPreferences`

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/SyncService.kt`

- [ ] **步骤 1：构造函数增加 prefs 参数**

在 `SyncService.kt` 第 46-59 行的构造函数参数列表末尾（`txtParser: TxtParser` 之后）追加一个参数，使主构造变为：

```kotlin
class SyncService(
    private val driveClient: GoogleDriveClient,
    private val authProvider: AuthProvider,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val syncLogDao: SyncLogDao,
    private val gson: Gson,
    private val epubParser: EpubParser,
    private val txtParser: TxtParser,
    private val prefs: SyncPreferences
) {
```

- [ ] **步骤 2：删除内部直接实例化的 prefs**

删除 `SyncService.kt` 第 63 行：

```kotlin
    private val prefs = SyncPreferences()
```

（`prefs` 现由构造参数提供，下面所有 `prefs.getLong/putLong/getStringSet/putStringSet` 用法不变。）

- [ ] **步骤 3：验证编译**

运行：`./gradlew :shared:desktopTest`
预期：编译通过（Koin 的 `singleOf(::SyncService)` 会按类型自动解析新依赖 `SyncPreferences`，无需改 `DataModule`）。测试全绿。

- [ ] **步骤 4：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/SyncService.kt
git commit -m "refactor: SyncService 改用构造注入 SyncPreferences"
```

---

## 任务 4：`SyncViewModel` 暴露自动同步开关

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/sync/SyncViewModel.kt`

- [ ] **步骤 1：新增 import**

在 `SyncViewModel.kt` 顶部 import 区（`ForegroundSyncController` import 附近）追加：

```kotlin
import com.ebookreader.simplebook.platform.SyncPreferences
```

- [ ] **步骤 2：构造函数注入 prefs**

将构造函数（第 19-24 行）改为：

```kotlin
class SyncViewModel(
    private val syncService: SyncService,
    val authProvider: AuthProvider,
    private val syncLogDao: SyncLogDao,
    private val foregroundSyncController: ForegroundSyncController,
    private val prefs: SyncPreferences
) : ViewModel() {
```

- [ ] **步骤 3：新增 autoSyncEnabled 状态**

在 `accountEmail` StateFlow 声明之后（约第 50 行后，`refreshAuthState()` 之前）插入：

```kotlin
    // 自动同步开关：默认开启（true），与升级前行为一致；关闭后 ON_START 不再自动同步。
    private val _autoSyncEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_SYNC, true))
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    fun toggleAutoSync() {
        val next = !_autoSyncEnabled.value
        prefs.putBoolean(KEY_AUTO_SYNC, next)
        _autoSyncEnabled.value = next
    }

    /**
     * 进入前台（ON_START）时调用：仅当已登录且开启自动同步时才触发同步。
     * 封装了原先散落在 App.kt / MainActivity.kt 的 isSignedIn 判断。
     */
    fun autoSyncIfEnabled() {
        if (isSignedIn.value && autoSyncEnabled.value) syncNow()
    }
```

- [ ] **步骤 4：新增 companion 常量**

在类末尾（`signOut()` 函数之后、类的闭合 `}` 之前）追加：

```kotlin
    companion object {
        private const val KEY_AUTO_SYNC = "auto_sync"
    }
```

- [ ] **步骤 5：验证编译**

运行：`./gradlew :shared:desktopTest`
预期：编译通过（`viewModelOf(::SyncViewModel)` 自动解析新参数）。测试全绿。

- [ ] **步骤 6：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/sync/SyncViewModel.kt
git commit -m "feat: SyncViewModel 暴露自动同步开关与 autoSyncIfEnabled"
```

---

## 任务 5：ON_START 生命周期监听改调 `autoSyncIfEnabled()`

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt`
- 修改：`androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt`

- [ ] **步骤 1：App.kt**

将 `App.kt` 第 56-62 行的 observer：

```kotlin
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        if (syncViewModel.isSignedIn.value) {
                            syncViewModel.syncNow()
                        }
                    }
                }
```

改为：

```kotlin
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        syncViewModel.autoSyncIfEnabled()
                    }
                }
```

- [ ] **步骤 2：MainActivity.kt**

将 `MainActivity.kt` 第 167-173 行的 observer：

```kotlin
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_START) {
                                    if (syncViewModel.isSignedIn.value) {
                                        syncViewModel.syncNow()
                                    }
                                }
                            }
```

改为：

```kotlin
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_START) {
                                    syncViewModel.autoSyncIfEnabled()
                                }
                            }
```

- [ ] **步骤 3：验证编译（两端）**

运行：`./gradlew :shared:desktopTest` 然后 `./gradlew :androidApp:assembleDebug`
预期：均编译通过。

- [ ] **步骤 4：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt \
        androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt
git commit -m "feat: ON_START 改调 autoSyncIfEnabled，受自动同步开关控制"
```

---

## 任务 6：`AppStrings` 新增同步设置文案

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/AppStrings.kt`

- [ ] **步骤 1：数据类新增三字段**

在 `AppStrings` 数据类中，`syncDescription` 字段（第 51 行）之后插入三行：

```kotlin
    val syncDescription: String,
    val syncSettingsTitle: String,
    val syncSettingsDescription: String,
    val autoSyncLabel: String,
    val syncConflicts: String,
```

- [ ] **步骤 2：英文分支赋值**

在 `getStrings()` 的 `"en"` 分支，`syncDescription = ...` 行（第 145 行）之后插入：

```kotlin
        syncDescription = "Sync your books and reading progress across devices",
        syncSettingsTitle = "Sync Settings",
        syncSettingsDescription = "When off, sync only when you tap Sync now",
        autoSyncLabel = "Auto Sync",
        syncConflicts = "Resolve Conflicts",
```

- [ ] **步骤 3：中文分支赋值**

在 `getStrings()` 的 `else`（中文）分支，`syncDescription = ...` 行（第 233 行）之后插入：

```kotlin
        syncDescription = "在设备之间同步您的书籍和阅读进度",
        syncSettingsTitle = "同步设置",
        syncSettingsDescription = "关闭后仅在点击「立即同步」时同步",
        autoSyncLabel = "自动同步",
        syncConflicts = "解决冲突",
```

- [ ] **步骤 4：验证编译**

运行：`./gradlew :shared:desktopTest`
预期：编译通过、测试全绿。

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/AppStrings.kt
git commit -m "feat: AppStrings 新增同步设置文案（中英文）"
```

---

## 任务 7：设置页新增「同步设置」分区与 Switch

**文件：**
- 修改：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/settings/SettingsScreen.kt`

- [ ] **步骤 1：新增 Switch import**

在 `SettingsScreen.kt` 的 material3 import 区（`import androidx.compose.material3.Slider` 附近，第 38 行前后）追加：

```kotlin
import androidx.compose.material3.Switch
```

- [ ] **步骤 2：收集 autoSyncEnabled 状态**

在第 89 行 `val lastSyncedAt by syncViewModel.lastSyncedAt.collectAsState()` 之后追加一行：

```kotlin
    val autoSyncEnabled by syncViewModel.autoSyncEnabled.collectAsState()
```

- [ ] **步骤 3：插入「同步设置」分区**

在第 279 行（Google Drive 同步分区的闭合 `}`）与第 281 行（`// ── 从 Drive 导入 ──`）之间插入：

```kotlin

            // ── 同步设置 ──
            if (isSignedIn) {
                HorizontalDivider()
                SectionHeader(strings.syncSettingsTitle)
                Text(
                    strings.syncSettingsDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(strings.autoSyncLabel, style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = autoSyncEnabled,
                        onCheckedChange = { syncViewModel.toggleAutoSync() }
                    )
                }
            }
```

> 仅在 `isSignedIn` 时渲染：未登录时自动/手动无意义，且与现有「账号相关操作仅登录后出现」一致。

- [ ] **步骤 4：验证编译**

运行：`./gradlew :shared:desktopTest`
预期：编译通过、测试全绿。

- [ ] **步骤 5：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/settings/SettingsScreen.kt
git commit -m "feat: 设置页新增「同步设置」分区与自动同步开关"
```

---

## 任务 8：整体验证（构建 + 手动）

- [ ] **步骤 1：跑全部桌面测试**

运行：`./gradlew :shared:desktopTest`
预期：全部测试 PASS（含新增 `SyncPreferencesTest` 的 3 个用例与既有测试）。

- [ ] **步骤 2：构建 Android**

运行：`./gradlew :androidApp:assembleDebug`
预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：手动验证清单（任选一端运行：`./gradlew :desktopApp:run` 或装 Android debug 包）**

1. 登录 Google Drive 后，设置页在「Google Drive 同步」与「从 Drive 导入」之间出现「同步设置」分区，含「自动同步」Switch，**默认开启**。
2. 点 Switch 关闭 → 开关变为关闭态。
3. **完全退出并重启应用** → 「同步设置」开关仍为关闭（状态已持久化）。
4. 开关关闭时，切到别的应用再切回来（触发 ON_START）→ **不应**触发同步（看日志无 `syncAll: starting`；Drive 无新请求）。
5. 点「立即同步」按钮 → **仍能**手动触发同步（手动模式只关自动）。
6. 再把开关打开 → 切回前台时会自动同步一次。
7. 退出登录 → 「同步设置」分区消失。

- [ ] **步骤 4：最终 commit（如有验证中发现的修复）**

```bash
git add -A
git commit -m "test: 整体验证通过（desktopTest + assembleDebug + 手动清单）"
```
（若验证中无需改动，跳过此步。）

---

## 自检结果

**规格覆盖度：** 规格 5.1（SyncPreferences boolean）→ 任务 1；5.2（Koin）→ 任务 2+3；5.3（ViewModel）→ 任务 4；5.4（门控）→ 任务 5；5.5（UI）→ 任务 7；5.6（文案）→ 任务 6；5.7（测试）→ 任务 1 的真实单测 + 任务 8 手动清单（已在文首「测试策略说明」中说明对规格 VM 单测的调整及原因）。无遗漏。

**占位符扫描：** 无 TODO / 待定 / 「类似上文」，所有代码步骤均含完整代码块。

**类型一致性：** `getBoolean(key, default)` / `putBoolean(key, value)`、`autoSyncEnabled` / `toggleAutoSync()` / `autoSyncIfEnabled()`、`KEY_AUTO_SYNC = "auto_sync"`、`syncSettingsTitle` / `syncSettingsDescription` / `autoSyncLabel` 在各任务间命名一致。Koin 构造注入无需手改 `AppModule`/`DataModule`（`viewModelOf` / `singleOf` 按类型自动解析）。
