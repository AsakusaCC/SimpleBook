# 同步设置（自动/手动同步开关）设计

- 日期：2026-07-27
- 状态：已批准（待实现）
- 关联：[2026-05-25-v1-google-drive-sync-design.md](./2026-05-25-v1-google-drive-sync-design.md)

## 1. 背景与动机

手机息屏/亮屏、切应用再回来，会反复触发应用进入前台（`Lifecycle.Event.ON_START`），而当前 ON_START 监听只要已登录就调用 `syncNow()`，导致 Google Drive 同步被高频、无意义地触发——既耗电耗流量，也给 Drive API 带来不必要的压力。

用户希望在设置页加入一个「自动同步 / 手动同步」开关：切到手动后，进前台不再自动同步，只有用户主动点击「立即同步」时才同步。

## 2. 目标与非目标

**目标**

- 在设置页「Google Drive 同步」与「从 Drive 导入」之间新增独立分区「同步设置」，内含一个 Switch 开关。
- 开关默认开启（自动同步），与现有行为向后兼容。
- 关闭后，ON_START 不再自动触发同步；其它同步入口（登录成功、「立即同步」按钮、书架同步图标）不受影响。
- 开关状态跨重启持久化。

**非目标**

- 不引入定时同步 / WorkManager（未来可扩展，本次不做）。
- 不重构 App.kt 与 MainActivity.kt 中重复的生命周期监听结构（仅修改其内部判断逻辑）。
- 不改动同步算法本身（LWW / 软删除逻辑不变）。

## 3. 现状（关键代码位置）

- 设置页：`shared/src/commonMain/.../ui/settings/SettingsScreen.kt`
  - 「Google Drive 同步」分区：第 226-279 行
  - 「从 Drive 导入」分区：第 281-339 行
  - 每个分区统一为 `HorizontalDivider() + SectionHeader(彩条标题) + 说明 + 内容`
- 自动同步触发点（ON_START，两处逻辑重复）：
  - `shared/src/commonMain/.../App.kt:54-67`
  - `androidApp/src/main/.../MainActivity.kt:165-178`
  - 现状：`if (syncViewModel.isSignedIn.value) syncViewModel.syncNow()`
- 其它 `syncNow()` 调用点（**本次不改**）：登录成功（`SyncViewModel.signIn`、`MainActivity.kt:141`）、「立即同步」按钮（`SettingsScreen.kt:255`）、书架同步图标（`BookListScreen.kt:184`）。
- 同步入口：`SyncViewModel.syncNow()` → `SyncService.syncAll()`。
- 持久化（两套）：
  - `SyncPreferences`（同步专用，expect/actual；Android 用 SharedPreferences `"sync_prefs"`，Desktop 用 Properties `~/Library/SimpleBook/sync_prefs.properties`）。**未进 Koin**，当前在 `SyncService` 内 `private val prefs = SyncPreferences()` 直接实例化。仅支持 Long / StringSet。
  - `SettingsDataStore`（阅读设置，已进 Koin）。
- 文案：`AppStrings` 数据类管理（非 strings.xml），中英文在 `getStrings()` 的 `else`（中文）/ 分支赋值。
- UI：项目目前**无任何 `material3.Switch` 用例**。

## 4. 方案选型

**采用方案 A：扩展 `SyncPreferences` 加 boolean 支持 + 纳入 Koin 单例。**

候选方案 B（塞进 `SettingsDataStore` + `ReaderSettings`）被否：自动同步语义上属于同步设置，不应混入阅读设置；且会让 `SyncViewModel` 反向依赖 `SettingsDataStore`，跨职责边界。

方案 A 的额外收益：顺手修正 `SyncPreferences` 未进 DI 的小瑕疵，全应用共用单例。

## 5. 详细设计

### 5.1 持久化层 — `SyncPreferences`

`commonMain` expect 新增：
```kotlin
fun getBoolean(key: String, default: Boolean = false): Boolean
fun putBoolean(key: String, value: Boolean)
```

- Android actual（SharedPreferences）：`prefs.getBoolean(key, default)` / `prefs.edit { putBoolean(key, value) }`。
- Desktop actual（Properties）：`put` 存 `value.toString()`，`get` 读后 `.toBoolean()`。

新 key 常量：`"auto_sync"`，默认值 `true`（与「默认开启」一致）。

### 5.2 DI — 纳入 Koin

- 将 `SyncPreferences` 注册为 `single`（沿用 `SettingsDataStore` 在 `PlatformModule` 的注册模式，androidMain / desktopMain 各自注册）。
- `SyncService`：把 `private val prefs = SyncPreferences()` 改为构造注入 `SyncService(..., private val prefs: SyncPreferences)`，并更新其构造/Koin 注册。
- `SyncViewModel`：构造注入 `SyncPreferences`。

### 5.3 ViewModel 层 — `SyncViewModel`

新增：
```kotlin
val autoSyncEnabled: StateFlow<Boolean>   // 初始化读 prefs.getBoolean("auto_sync", true)

fun toggleAutoSync() {
    val next = !autoSyncEnabled.value
    prefs.putBoolean("auto_sync", next)   // 先持久化
    _autoSyncEnabled.value = next         // 再更新状态
}

fun autoSyncIfEnabled() {
    if (isSignedIn.value && autoSyncEnabled.value) syncNow()
}
```

`autoSyncIfEnabled()` 封装了原 ON_START 的判断逻辑，供生命周期监听调用。

### 5.4 触发门控 — 生命周期监听

`App.kt:54-67` 与 `MainActivity.kt:165-178` 的 ON_START 分支改为：
```kotlin
if (event == Lifecycle.Event.ON_START) {
    syncViewModel.autoSyncIfEnabled()
}
```
（原先的 `isSignedIn` 判断已并入 `autoSyncIfEnabled()`。）

### 5.5 UI 层 — `SettingsScreen`

在「Google Drive 同步」分区（~279 行结束）与「从 Drive 导入」分区（~281 行开始）之间插入：
```kotlin
// ── 同步设置 ──
HorizontalDivider()
SectionHeader(strings.syncSettingsTitle)
Text(strings.syncSettingsDescription, ...)
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(strings.autoSyncLabel)
    Switch(
        checked = autoSyncEnabled,
        onCheckedChange = { syncViewModel.toggleAutoSync() }
    )
}
```

- 引入 `androidx.compose.material3.Switch`。
- **可见性**：仅在 `isSignedIn` 为真时渲染该分区（未登录时自动/手动无意义，与现有「账号相关操作仅登录后出现」一致）。
- `autoSyncEnabled` 由 `SettingsScreen` 从 `SyncViewModel` 取用（与现有 `isSignedIn` / `lastSyncedAt` 等同一路径）。

### 5.6 文案 — `AppStrings`

数据类新增三字段，`getStrings()` 中英文分支赋值：

| 字段 | 中文 | 英文 |
|---|---|---|
| `syncSettingsTitle` | 同步设置 | Sync Settings |
| `syncSettingsDescription` | 关闭后仅在点击「立即同步」时同步 | When off, sync only when you tap Sync now |
| `autoSyncLabel` | 自动同步 | Auto Sync |

### 5.7 测试

- **ViewModel 测试**（mock `SyncService` + `SyncPreferences`）：
  - `autoSyncIfEnabled()`：`autoSyncEnabled=true && signedIn` → 触发 `syncAll`；二者任一为 false → 不触发。
  - `toggleAutoSync()`：StateFlow 翻转，且调用 `prefs.putBoolean("auto_sync", ...)` 持久化。
  - 初始化：`SyncPreferences.getBoolean("auto_sync", true)` 首次返回默认 `true`。
- **持久化往返**：在已有 `SyncPreferences` 测试可行的平台上补 `putBoolean/getBoolean` 往返用例。

## 6. 影响面 / 改动文件清单

| 层 | 文件 | 改动 |
|---|---|---|
| 持久化 | `SyncPreferences.kt`（commonMain expect + androidMain/desktopMain actual） | 加 `getBoolean/putBoolean` |
| DI | `PlatformModule.kt`（androidMain / desktopMain） | 注册 `SyncPreferences` single |
| DI/服务 | `SyncService.kt` | 构造注入 `SyncPreferences` |
| ViewModel | `SyncViewModel.kt` | 注入 prefs；`autoSyncEnabled` / `toggleAutoSync()` / `autoSyncIfEnabled()` |
| 门控 | `App.kt`、`MainActivity.kt` | ON_START 改调 `autoSyncIfEnabled()` |
| UI | `SettingsScreen.kt` | 新增「同步设置」分区 + Switch |
| 文案 | `AppStrings.kt` | 三字段 + 中英文 |
| 测试 | SyncViewModel 测试、SyncPreferences 测试 | 见 5.7 |

## 7. 兼容性

- 默认 `true`，升级后老用户行为不变。
- 桌面端 `sync_prefs.properties` 新增 `auto_sync` 键，旧文件无此键时取默认 `true`，向后兼容。
- 不改动同步数据结构与算法，无数据迁移。
