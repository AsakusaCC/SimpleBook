# SimpleBook 桌面端 auth/token 加固设计文档

> 日期: 2026-07-23
> 状态: 已批准
> 分支: feat/kmp-desktop-migration

## 1. 背景与问题

KMP 桌面迁移（Phase 1-7）打通了桌面端登录、同步、阅读，但 auth/token 链路有 4 个相互耦合的遗留缺陷，导致**登录后同步无法长期稳定工作**：

### 1.1 四个遗留缺陷

| 缺陷 | 现象 | 根因（已核实当前代码） |
|------|------|------|
| **A1 access_token 过期无刷新** | 登录满 1h 后 Drive 调用 401，同步失败（被 SyncService catch，不崩进程） | `desktopMain AuthProvider.kt` 存了 `accessToken`/`refreshToken` 但**无任何刷新逻辑**；`expires_in` 未持久化 |
| **A2 reauth 流程被降级** | 需要重新授权时（如 scope 缺失、refresh 失效）无便捷入口 | `SyncService.kt:69-72` 把 `_reauthIntent` 改成 `MutableStateFlow<Nothing?>(null)`，两处 catch（`syncAll`/`importFromDriveFolder`）只赋 `null` |
| **A3 token 明文存储** | `~/Library/SimpleBook/tokens.properties` 明文（虽 0600） | `TokenStorage.kt:6` 自带 TODO，一直未迁 Keychain |
| **A4 Drive 同步 SSL 失败** | `SSLHandshakeException: Remote host terminated the handshake` / `SSL peer shut down incorrectly` | **TLS 层失败（认证之前）**，与 access_token 过期（401，握手成功后才返回）无关；需 live 复现定位真因 |

### 1.2 关键纠偏（记忆假设 vs 实测）

- **A4 不是 A1 的衍生**：旧记忆「SSL 失败可能是 access_token 过期遗留」有误。SSL handshake 在 HTTP 层之前，token 401 永远发生在握手成功之后。两者独立，但都需修。
- **`GoogleDriveClient` 的 null-token 行为**：credential 工厂 `() -> DriveCredential?` 返回 null 时，各 Drive 方法**静默返回 null/空**（`getAppFolderId` 除外，恒返回 `"appDataFolder"`），不抛异常。因此 A1 的刷新失败**必须抛异常**（而非返回 null），否则错误会被吞或变成「Failed to create book folder」之类误导信息。

### 1.3 目标

让桌面端登录后能**持续 >1h 稳定同步**：token 自动刷新、refresh 失效时有清晰的重新登录入口、token 入 macOS Keychain、SSL 链路稳定。同时把被迁移降级的 **Android reauth 流程一并恢复**（贴近 v0.8.6 行为）。

> 本 spec 只覆盖 auth/token 加固（A1-A4）。**Android release 签名**是独立任务，另起一份短 spec。

## 2. 核心决策

| 维度 | 决策 | 理由 |
|------|------|------|
| 刷新触发点 | proactive，放在 `AuthProvider.getAccessToken()` 内（每次 Drive 调用现取 token 时检查过期） | `GoogleDriveClient` 工厂每次调用都取 token → 单次同步跨多小时也不会中途过期；无需额外接线 |
| 刷新失败信号 | 抛 commonMain `AuthExpiredException`，经 Drive.execute 传到 SyncService catch | null 会被 Drive 静默吞；异常能干净触发 reauth |
| reauth 跨端抽象 | `expect class ReauthRequest`（不透明）+ `expect fun Throwable.toReauthRequest()` | commonMain 不能引用 `android.content.Intent`；不透明类型让 SyncService 只依赖一个 expect |
| Keychain 访问 | `/usr/bin/security` CLI（generic-password） | 纯 JVM、无原生依赖；AuthProvider 内存缓存 token，Keychain 仅登录/刷新/登出/启动读写，spawn 开销可控 |
| refresh_token 获取 | signIn authUrl 补 `access_type=offline`，**不**加 `prompt=consent` | 确保 Google 发 refresh_token；保留静默体验（强制 consent 每次弹窗，扰民） |
| SSL 修复方式 | live 复现后按栈定方案，最可能落点「重试」 | TLS 层失败根因未明，不能预判实现 |

## 3. A1 · access_token 自动刷新

### 3.1 存储变更

`TokenStorage` 增加 `expiresAt: Long?`（epoch ms）字段，与 access/refresh/email 同存同取。`AuthProvider` 启动时从 storage 载入到内存 `var expiresAt`。

### 3.2 `getAccessToken()` 新逻辑

```
fun getAccessToken(): String? {
    val token = accessToken ?: return null              // 未登录
    if (expiresAt == null || now() + 60_000 < expiresAt) return token  // 仍有效（留 60s 余量）
    // 临近/过期 → 刷新
    val refreshed = refreshAccessToken()                // blocking, Mutex 去重
    return refreshed ?: throw AuthExpiredException()    // 刷新失败 → 抛异常触发 reauth
}
```

- **60s 余量**：避免 token 在发请求途中刚好过期。
- **并发去重**：`Mutex` 保护 `refreshAccessToken()`，并发 Drive 调用只触发一次刷新，其他等待结果复用。

### 3.3 `refreshAccessToken(): String?`

POST `https://oauth2.googleapis.com/token`，body：
```
grant_type=refresh_token
&refresh_token=<存盘的 refresh>
&client_id=<CLIENT_ID>
&client_secret=<CLIENT_SECRET>
```
- 成功（200，含 `access_token` + `expires_in`）：更新内存 `accessToken`/`expiresAt`，若响应含新 `refresh_token`（Google 偶尔轮换）也更新；`tokenStorage.saveTokens(...)` 持久化（Keychain）。返回新 token。
- 失败（`invalid_grant` / 4xx / 网络）：**返回 null** → `getAccessToken` 抛 `AuthExpiredException`。**不**清 token（保留 refresh，留给 reauth/重试判断；UI 重登后覆盖）。

### 3.4 signIn 补 access_type=offline

`desktopMain AuthProvider.kt` 的 `authUrl` 拼接增加 `&access_type=offline`。`exchangeCodeForToken` 已 `takeIf{!isJsonNull}` 处理 refresh 可空——保留。

**老用户迁移说明**：修复前已登录且 `tokens.properties`/Keychain 未存 refresh_token 的，首次过期刷新会失败 → 触发 reauth → 重登一次即正常（之后 refresh 持久化）。

### 3.5 可测性

提取 `TokenEndpoint` 接口（`fun postForm(params: String): JsonObject` 或返回已解析结果），`AuthProvider` 注入它：
- 真实 impl = `HttpTokenEndpoint`（现有 `httpPost` 逻辑）。
- 测试用 fake：返回合法 token JSON / `invalid_grant` 错误 / 抛 IOException。

**TDD 用例**：
1. 有效 token（未到 expiresAt）→ 不调 endpoint，直接返回。
2. 过期 + 刷新成功 → 调 endpoint 一次，返回新 token，内存/storage 更新。
3. 过期 + `invalid_grant` → 抛 `AuthExpiredException`，accessToken 不变。
4. 并发两次 getAccessToken 且都过期 → endpoint 只被调一次（Mutex 验证）。
5. 刷新响应含新 refresh_token → refresh 字段被更新。

## 4. A2 · reauth 双端恢复

### 4.1 commonMain 抽象

新增（`platform` 包）：
```kotlin
// commonMain
class AuthExpiredException(message: String = "Access token refresh failed") : Exception(message)

expect class ReauthRequest()
expect fun Throwable.toReauthRequest(): ReauthRequest?
```

### 4.2 平台实现

**desktopMain**：
```kotlin
actual class ReauthRequest actual constructor()   // 空壳：桌面重登无需 payload
actual fun Throwable.toReauthRequest(): ReauthRequest? =
    if (this is AuthExpiredException) ReauthRequest() else null
```

**androidMain**：
```kotlin
actual class ReauthRequest actual constructor(val intent: android.content.Intent)
actual fun Throwable.toReauthRequest(): ReauthRequest? {
    // google-api-client-android 的 UserRecoverableAuthIOException
    if (this is com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
        return ReauthRequest(this.intent)
    }
    return null
}
```

> Android `AuthErrors.kt` 现有 `isUserRecoverableAuthError()` 保留（向后兼容），新逻辑统一走 `toReauthRequest()`。

### 4.3 SyncService 改造

`SyncService.kt`：
```kotlin
private val _reauthRequest = MutableStateFlow<ReauthRequest?>(null)
val reauthRequest: StateFlow<ReauthRequest?> = _reauthRequest.asStateFlow()
fun consumeReauthRequest() { _reauthRequest.value = null }
```
两处 catch（`syncAll:109`、`importFromDriveFolder:1222`）统一改为：
```kotlin
} catch (e: Exception) {
    val req = e.toReauthRequest()
    if (req != null) {
        _reauthRequest.value = req
        _syncStatus.value = SyncStatus.Error("需要重新授权 Google 权限")  // import 路径用 ImportStatus.Error
    } else {
        logE(...); _syncStatus.value = SyncStatus.Error(e.message ?: "同步失败")
    }
}
```
旧 `isUserRecoverableAuthError`（commonMain expect + desktop/android actual，仅 SyncService 两处引用）改为 `toReauthRequest()` 后**无其他引用** → 删除 `AuthErrors.kt` 三件套（common expect + 2 actual），消除死代码。

### 4.4 UI 接线

- **桌面** `SettingsScreen`：`val reauth by syncService.reauthRequest.collectAsState()`（经 SyncViewModel 透传）；非 null → 显示对话框「登录已过期，请重新登录」+「重新登录」按钮 → `syncViewModel.signIn()` → 成功后 `syncViewModel.consumeReauthRequest()`。
- **Android** `MainActivity`：`LaunchedEffect(reauthRequest)` 观察；非 null → **复用现有 `signInLauncher`**（同为 `StartActivityForResult` 契约，回调 `handleSignInResult` 对 re-consent 结果同样适用）`launch(reauthRequest.intent)` → 回调 `authManager.handleSignInResult(data)` + `syncViewModel.refreshAuthState()` + `consumeReauthRequest()`。

### 4.5 SyncViewModel 透传

`SyncViewModel` 暴露 `reauthRequest`（来自 `syncService.reauthRequest`）与 `consumeReauthRequest()`，供 UI collect。

### 4.6 测试

- **desktopTest**：`toReauthRequest()`——`AuthExpiredException`→非 null；`IOException`/普通 Exception→null。
- **Android**：映射是一行类型判断且依赖 `google-api-client-android`，不引入 Robolectric；靠 **真机验证**（构造/触发 scope 缺失场景）。

## 5. A3 · token → macOS Keychain

### 5.1 SecretStore 抽象（可测）

新增 `desktopMain`：
```kotlin
interface SecretStore {
    fun read(account: String): String?
    fun write(account: String, value: String)
    fun delete(account: String)
}
```
- 真实 impl `SecurityCliStore`：spawn `/usr/bin/security`，service 固定 `com.ebookreader.simplebook`：
  - read: `security find-generic-password -s <svc> -a <account> -w`
  - write: `security add-generic-password -U -s <svc> -a <account> -w <value>`
  - delete: `security delete-generic-password -s <svc> -a <account>`（忽略「不存在」退出码）
- 测试用 `InMemorySecretStore` fake。

### 5.2 TokenStorage 改造

`TokenStorage` 后端由 Properties 文件换 `SecretStore`：
- 单 account `tokens`，value = `Base64(JSON{access_token, refresh_token, expires_at, user_email})`。
- 接口在现有 `getAccessToken/getRefreshToken/getUserEmail/saveTokens/clear` 基础上**新增 `getExpiresAt()`**，`saveTokens` 增加 `expiresAt: Long?` 形参（A1 需要）。`AuthProvider` 是唯一调用方，随 A1 一并更新，影响范围可控。
- 构造注入 `SecretStore`（默认 `SecurityCliStore()`），便于测试注入 fake。

### 5.3 迁移（一次性）

`TokenStorage.init`（或 `AuthProvider` 初始化）：
1. Keychain（`tokens` account）为空 **且** 旧 `~/Library/SimpleBook/tokens.properties` 存在 → 解析旧文件 → `saveTokens(...)` 写 Keychain。
2. **删除旧文件**（直接 `delete()`，开发期不做 shred 写零）。
3. 仅执行一次（Keychain 非空后不再看旧文件）。

### 5.4 安全取舍（已知）

- `/usr/bin/security` 的 `-w <value>` 把 token 经 argv 传入，**短暂可见于 `ps`**（子秒级）。`security` CLI 不支持 stdin 读 `-w`。缓解：owner-only 机器、OAuth token 可吊销、刷新窗口每小时一次。可接受。
- AuthProvider 内存缓存 token（已是现状）→ Keychain 读写只在 登录/刷新/登出/启动 发生，**非每次 Drive 调用** → spawn 开销可控。

### 5.5 测试（改造现有 TokenStorageTest）

- 换 `InMemorySecretStore` 注入，保留全部既有用例语义（round-trip / 跨实例持久化 / clear / null 可选字段）。
- 新增：`expiresAt` 读写 round-trip；迁移用例（fake 旧文件存在 + Keychain 空 → 导入 + 旧文件删除）。

## 6. A4 · Drive SSL 定位

### 6.1 性质

bug 诊断，非功能。无单测，靠手动 run + 栈分析。

### 6.2 复现 + 决策树

1. `./gradlew :desktopApp:run` → 登录 → 触发同步 → 抓完整堆栈 + 出错 host/操作。
2. 区分出错层：
   - **AuthProvider httpPost**（token exchange/userinfo，`HttpURLConnection`）→ 网络层通用问题。
   - **Drive execute**（`GoogleDriveClient`，`NetHttpTransport`）→ google-api-client 传输层问题。
3. 按栈定方案：
   - **① 瞬时断连 / VPN 拦截**（最可能，宿主 Mac 能 `curl googleapis` 但 JVM 链路偶发被重置）→ `GoogleDriveClient` 各 `.execute()` 包一层重试（`SSLHandshakeException`/`IOException` 指数退避，最多 3 次）。
   - **② TLS 版本**（旧 JDK 默认 TLSv1.1）→ 强制 `NetHttpTransport`/`HttpURLConnection` 用 TLSv1.3。
   - **③ truststore 缺证书** → 指向 JBR cacerts。
   - **④ 实为 401 误读** → A1 完成后自愈，回归验证。

### 6.3 输出

诊断结论 + 最小修复（很可能是一段重试封装或 TLS 配置）写入实现计划/提交说明。

## 7. 测试策略总览

| 层 | 用例 | 位置 |
|----|------|------|
| A1 刷新 | 过期→刷新成功 / invalid_grant→AuthExpiredException / 并发只刷一次 / refresh 轮换 | `shared/desktopTest`（新 `AuthProviderRefreshTest`） |
| A2 reauth 映射 | desktop: AuthExpiredException→非 null / 普通→null | `shared/desktopTest`（新 `ReauthRequestTest`） |
| A3 Keychain | round-trip / 跨实例 / clear / expiresAt / 迁移 | `shared/desktopTest`（改造 `TokenStorageTest`） |
| A4 SSL | 无单测 | 手动 `desktopApp:run` 验证 |

现有 23 个 desktopTest 保持绿色。

## 8. 验证步骤

```bash
# 单测
./gradlew :shared:desktopTest

# 桌面端实测
./gradlew :desktopApp:run
# 登录 → 手动把 storage 里 expiresAt 改成已过期 → 触发同步 → 确认自动刷新 + 同步成功
# SSL：正常网络 + VPN 两种环境各触发一次同步，观察日志

# Android reauth（真机）
./gradlew :androidApp:assembleDebug
```

## 9. 范围之外 / 已知取舍

- **不做** Drive 401 反应式重试（proactive 刷新已覆盖常规场景；SSL 重试在 A4 按需加）。
- **不做** `prompt=consent`（保留静默登录体验）。
- **不引入** JNA / Robolectric / androidTest 源集。
- **不碰** release 签名（独立 spec B）。
- Keychain `-w` argv 可见性为已知取舍，不做额外加固（shredding/进程隔离超出范围）。
- 老用户首次过期刷新失败需重登一次（无 refresh_token）——迁移说明，不做自动静默重登。
