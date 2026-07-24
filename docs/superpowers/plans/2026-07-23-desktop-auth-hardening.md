# 桌面 auth/token 加固 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让桌面端登录后能持续 >1h 稳定同步——token 自动刷新、refresh 失效有重新登录入口、token 入 macOS Keychain、SSL 链路稳定；同时恢复 Android reauth 流程。

**架构：** ① `TokenStorage` 后端由明文 Properties 换 `/usr/bin/security` Keychain（`SecretStore` 抽象，内存缓存）；② 桌面 `AuthProvider.getAccessToken()` 内 proactive 刷新（过期用 refresh_token 换新，失败抛 commonMain `AuthExpiredException`）；③ `expect class ReauthRequest` + `Throwable.toReauthRequest()` 跨端抽象，`SyncService` 据此发 reauth 信号，UI（桌面重登 / Android launch Intent）消费；④ Drive SSL live 复现后按栈定方案。

**技术栈：** Kotlin 2.1 / KMP（commonMain + androidMain + desktopMain）/ Koin 4 / google-api-client（Drive）/ HttpURLConnection（OAuth）/ Gson / `/usr/bin/security` / JUnit（kotlin-test）。

**规格：** `docs/superpowers/specs/2026-07-23-desktop-auth-hardening-design.md`

**关键约束（勿踩）：**
- `expect class AuthProvider()` 是无参构造——desktop actual 不能改签名，测试注入用 `internal var` + 懒加载兜底（见任务 2）。
- `GoogleDriveClient` credential 工厂返回 null 时各方法**静默返回 null/空**——所以刷新失败必须**抛异常**而非返回 null。
- `TokenStorage()` 默认构造会跑迁移（读真 Keychain + 真 `~/Library/SimpleBook/tokens.properties`）——**测试绝不能默认构造**，必须注入 `InMemorySecretStore` + temp 文件。
- 构建环境：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`。

**任务依赖：** 任务1（Keychain 存储）→ 任务2（刷新）→ 任务3（reauth 抽象）→ 任务4（reauth 接线）。任务5（SSL）独立，最后做。

---

## 文件结构

| 文件 | 职责 | 动作 |
|------|------|------|
| `shared/src/desktopMain/.../platform/SecretStore.kt` | `SecretStore` 接口 + `SecurityCliStore`（spawn `/usr/bin/security`） | 新建 |
| `shared/src/desktopMain/.../platform/TokenStorage.kt` | token 持久化（Keychain 后端 + 一次性迁移） | 重写 |
| `shared/src/desktopTest/.../platform/InMemorySecretStore.kt` | 测试用内存 `SecretStore` | 新建 |
| `shared/src/desktopTest/.../platform/TokenStorageTest.kt` | TokenStorage 测试（换内存后端 + 迁移 + expiresAt） | 重写 |
| `shared/src/commonMain/.../platform/AuthExpiredException.kt` | 刷新失败异常（commonMain） | 新建 |
| `shared/src/desktopMain/.../platform/TokenEndpoint.kt` | `TokenEndpoint` 接口 + `HttpTokenEndpoint`（OAuth HTTP） + `OAuthConfig` | 新建 |
| `shared/src/desktopMain/.../platform/AuthProvider.kt` | 桌面 auth：懒加载 + proactive 刷新 + access_type=offline | 改造 |
| `shared/src/desktopTest/.../platform/AuthProviderRefreshTest.kt` | 刷新逻辑测试 | 新建 |
| `shared/src/commonMain/.../platform/ReauthRequest.kt` | `expect class ReauthRequest(cause: Throwable)` + `expect fun toReauthRequest()` | 新建 |
| `shared/src/desktopMain/.../platform/ReauthRequest.kt` | desktop actual（cause=AuthExpiredException） | 新建 |
| `shared/src/androidMain/.../platform/ReauthRequest.kt` | android actual（cause=UserRecoverableAuthIOException，UI 从 cause 取 Intent） | 新建 |
| `shared/src/{common,desktop,android}Main/.../platform/AuthErrors.kt` | 旧 `isUserRecoverableAuthError`（死码） | 删除（3 文件） |
| `shared/src/desktopTest/.../platform/ReauthRequestTest.kt` | desktop toReauthRequest 映射测试 | 新建 |
| `shared/src/commonMain/.../domain/service/SyncService.kt` | `_reauthRequest` StateFlow + 两处 catch 改 `toReauthRequest()` | 改造 |
| `shared/src/commonMain/.../ui/sync/SyncViewModel.kt` | 暴露 `reauthRequest` + `consumeReauthRequest()` | 改造 |
| `shared/src/commonMain/.../ui/navigation/SimpleBookNavHost.kt` | 新增 `onReauthClick` 透传 | 改造 |
| `shared/src/commonMain/.../ui/settings/SettingsScreen.kt` | 收集 reauth 信号 + 重新登录对话框 | 改造 |
| `shared/src/commonMain/.../App.kt` | 桌面 `onReauthClick = { syncViewModel.signIn() }` | 改造 |
| `androidApp/src/main/.../MainActivity.kt` | Android `onReauthClick = { req -> launcher.launch(req.intent) }` | 改造 |

---

## 任务 1：Keychain 后端 TokenStorage（A3）

**文件：**
- 新建：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/SecretStore.kt`
- 重写：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/TokenStorage.kt`
- 新建：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/InMemorySecretStore.kt`
- 重写：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/TokenStorageTest.kt`

- [ ] **步骤 1.1：新建 `SecretStore.kt`**

```kotlin
package com.ebookreader.simplebook.platform

/**
 * 抽象的 secrets 存储，便于在 Keychain（生产）与内存（测试）间切换。
 * TokenStorage 是唯一调用方。
 */
interface SecretStore {
    /** 读取指定 account 的值；不存在返回 null。 */
    fun read(account: String): String?
    /** 写入（存在则覆盖）。 */
    fun write(account: String, value: String)
    /** 删除；不存在不报错。 */
    fun delete(account: String)
}

/**
 * macOS Keychain 后端，spawn `/usr/bin/security` 读写 generic-password。
 * 所有 token 走同一 service 下、account="tokens" 的单一条目。
 *
 * 已知取舍：`-w <value>` 经 argv 传入，短暂可见于 `ps`（子秒级）。`security` CLI
 * 不支持 stdin 读 `-w`。缓解：owner-only 机器、OAuth token 可吊销、刷新每小时一次。
 */
internal class SecurityCliStore(
    private val service: String = "com.ebookreader.simplebook"
) : SecretStore {

    override fun read(account: String): String? {
        val out = runSecurity(listOf("find-generic-password", "-s", service, "-a", account, "-w"))
            ?: return null   // 条目不存在（退出码 44）或失败
        return out.trimEnd().let { pwd ->
            // 现代版本 `-w` 直接输出密码；老版本可能带 "password:" 前缀，兼容处理
            if (pwd.startsWith("password:")) pwd.removePrefix("password:").trim() else pwd
        }
    }

    override fun write(account: String, value: String) {
        runSecurity(listOf("add-generic-password", "-U", "-s", service, "-a", account, "-w", value))
    }

    override fun delete(account: String) {
        // 不存在（退出码 44）静默忽略
        runSecurity(listOf("delete-generic-password", "-s", service, "-a", account))
    }

    /** 返回 stdout（成功），null 表示非零退出码。 */
    private fun runSecurity(args: List<String>): String? {
        val proc = ProcessBuilder(listOf("/usr/bin/security") + args)
            .redirectErrorStream(true)
            .start()
        val out = proc.inputStream.bufferedReader().use { it.readText() }
        return if (proc.waitFor() == 0) out else null
    }
}
```

- [ ] **步骤 1.2：重写 `TokenStorage.kt`**

```kotlin
package com.ebookreader.simplebook.platform

import com.google.gson.Gson
import java.io.File
import java.util.Base64
import java.util.Properties

/**
 * Token 持久化。后端为 [SecretStore]（生产=macOS Keychain）。
 * 单 account 存一个 Base64(JSON) blob，包含 access/refresh/expiresAt/email。
 * AuthProvider 在内存缓存 token，本类仅在 登录/刷新/登出/启动 读写。
 */
class TokenStorage(
    private val secretStore: SecretStore = SecurityCliStore(),
    private val legacyFile: File = File(
        System.getProperty("user.home"), "Library/SimpleBook/tokens.properties"
    )
) {
    private val gson = Gson()

    init { migrateIfNeeded() }

    fun getAccessToken(): String? = loadBlob().accessToken
    fun getRefreshToken(): String? = loadBlob().refreshToken
    fun getExpiresAt(): Long? = loadBlob().expiresAt
    fun getUserEmail(): String? = loadBlob().email

    fun saveTokens(accessToken: String, refreshToken: String?, expiresAt: Long?, email: String?) {
        val blob = TokenBlob(accessToken, refreshToken, expiresAt, email)
        secretStore.write(ACCOUNT, encode(blob))
    }

    fun clear() {
        secretStore.delete(ACCOUNT)
    }

    /** 一次性迁移：Keychain 空 + 旧明文文件存在 → 导入后删文件。 */
    private fun migrateIfNeeded() {
        if (secretStore.read(ACCOUNT) != null) return      // 已在 Keychain
        if (!legacyFile.exists()) return
        val props = Properties()
        try {
            legacyFile.inputStream().use { props.load(it) }
        } catch (e: Exception) {
            return   // 旧文件不可读 → 跳过
        }
        val access = props.getProperty("access_token") ?: return
        saveTokens(
            accessToken = access,
            refreshToken = props.getProperty("refresh_token"),
            expiresAt = props.getProperty("expires_at")?.toLongOrNull(),
            email = props.getProperty("user_email")
        )
        legacyFile.delete()
    }

    private fun loadBlob(): TokenBlob {
        val raw = secretStore.read(ACCOUNT) ?: return TokenBlob()
        return try {
            gson.fromJson(String(Base64.getDecoder().decode(raw), Charsets.UTF_8), TokenBlob::class.java)
        } catch (e: Exception) {
            TokenBlob()   // 损坏 → 视为空
        }
    }

    private fun encode(blob: TokenBlob): String =
        Base64.getEncoder().encodeToString(gson.toJson(blob).toByteArray(Charsets.UTF_8))

    private data class TokenBlob(
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresAt: Long? = null,
        val email: String? = null
    )

    companion object {
        private const val ACCOUNT = "tokens"
    }
}
```

- [ ] **步骤 1.3：新建测试用 `InMemorySecretStore.kt`**

```kotlin
package com.ebookreader.simplebook.platform

/** 纯进程内 SecretStore，desktopTest 专用，绝不触碰真 Keychain。 */
class InMemorySecretStore : SecretStore {
    private val map = mutableMapOf<String, String>()
    override fun read(account: String): String? = map[account]
    override fun write(account: String, value: String) { map[account] = value }
    override fun delete(account: String) { map.remove(account) }
}
```

- [ ] **步骤 1.4：重写 `TokenStorageTest.kt`（先写测试，验证编译失败/语义）**

```kotlin
package com.ebookreader.simplebook.platform

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenStorageTest {

    private val tempDir: java.io.File = Files.createTempDirectory("tokenstorage-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newStorage(store: SecretStore = InMemorySecretStore()): TokenStorage =
        TokenStorage(store, java.io.File(tempDir, "nonexistent-legacy"))  // 无旧文件

    @Test
    fun emptyStorage_returnsNulls() {
        val storage = newStorage()
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getExpiresAt())
        assertNull(storage.getUserEmail())
    }

    @Test
    fun saveAndReadTokens_roundTripsAllFields() {
        val storage = newStorage()
        storage.saveTokens("access-123", "refresh-456", 1_700_000_000_000L, "user@example.com")
        assertEquals("access-123", storage.getAccessToken())
        assertEquals("refresh-456", storage.getRefreshToken())
        assertEquals(1_700_000_000_000L, storage.getExpiresAt())
        assertEquals("user@example.com", storage.getUserEmail())
    }

    @Test
    fun saveTokens_persistsAcrossInstances_sharingSameStore() {
        val store = InMemorySecretStore()
        TokenStorage(store, java.io.File(tempDir, "none")).saveTokens("a", "b", 123L, "c@example.com")
        val reloaded = TokenStorage(store, java.io.File(tempDir, "none"))
        assertEquals("a", reloaded.getAccessToken())
        assertEquals("b", reloaded.getRefreshToken())
        assertEquals(123L, reloaded.getExpiresAt())
        assertEquals("c@example.com", reloaded.getUserEmail())
    }

    @Test
    fun saveTokens_handlesNullOptionalFields() {
        val storage = newStorage()
        storage.saveTokens("only-access", null, null, null)
        assertEquals("only-access", storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getExpiresAt())
        assertNull(storage.getUserEmail())
    }

    @Test
    fun clear_removesAllFields() {
        val store = InMemorySecretStore()
        val storage = TokenStorage(store, java.io.File(tempDir, "none"))
        storage.saveTokens("a", "b", 1L, "c@example.com")
        storage.clear()
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getExpiresAt())
        assertNull(TokenStorage(store, java.io.File(tempDir, "none")).getAccessToken())
    }

    @Test
    fun migrateIfNeeded_importsLegacyFile_andDeletesIt() {
        val store = InMemorySecretStore()
        val legacy = java.io.File(tempDir, "tokens.properties")
        java.io.PrintWriter(legacy).use { w ->
            w.println("access_token=migrated-access")
            w.println("refresh_token=migrated-refresh")
            w.println("user_email=migrated@example.com")
            // 无 expires_at（老格式）
        }
        assertTrue(legacy.exists())

        val storage = TokenStorage(store, legacy)

        assertEquals("migrated-access", storage.getAccessToken())
        assertEquals("migrated-refresh", storage.getRefreshToken())
        assertEquals("migrated@example.com", storage.getUserEmail())
        assertNull(storage.getExpiresAt())
        assertFalse(legacy.exists(), "迁移后旧文件应被删除")
    }

    @Test
    fun migrateIfNeeded_skipsWhenKeychainAlreadyHasData() {
        val store = InMemorySecretStore()
        store.write("tokens", "existing-blob")
        val legacy = java.io.File(tempDir, "tokens.properties")
        java.io.PrintWriter(legacy).use { it.println("access_token=should-not-be-used") }

        val storage = TokenStorage(store, legacy)

        // Keychain 已有数据 → 不迁移，旧文件保留，读取走 Keychain（解析 existing-blob 失败→空）
        assertNull(storage.getAccessToken())
        assertTrue(legacy.exists(), "已迁移过则不动旧文件")
    }
}
```

- [ ] **步骤 1.5：运行测试验证通过**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.platform.TokenStorageTest"`
预期：PASS（7 用例全过）。若 `migrateIfNeeded_skipsWhenKeychainAlreadyHasData` 中 "existing-blob" 解析报错而非返回空，确认 `loadBlob` 的 try/catch 生效。

- [ ] **步骤 1.6：确认 desktopTest 全量仍绿**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest`
预期：PASS（原 23 测 + TokenStorage 新用例）。

- [ ] **步骤 1.7：Commit**

```bash
git add shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/SecretStore.kt \
        shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/TokenStorage.kt \
        shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/InMemorySecretStore.kt \
        shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/TokenStorageTest.kt
git commit -m "feat(security): token 迁移到 macOS Keychain（SecretStore/security CLI/一次性迁移）"
```

---

## 任务 2：access_token 自动刷新（A1）

**文件：**
- 新建：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/AuthExpiredException.kt`
- 新建：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/TokenEndpoint.kt`
- 改造：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/AuthProvider.kt`
- 新建：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/AuthProviderRefreshTest.kt`

- [ ] **步骤 2.1：新建 commonMain `AuthExpiredException.kt`**

```kotlin
package com.ebookreader.simplebook.platform

/**
 * 桌面 access_token 刷新失败（refresh_token 缺失 / invalid_grant）时抛出。
 * 经 Drive.execute() 传到 SyncService catch，由 [toReauthRequest] 映射为 reauth 信号。
 */
class AuthExpiredException(
    message: String = "Access token refresh failed; re-authentication required"
) : Exception(message)
```

- [ ] **步骤 2.2：新建 `TokenEndpoint.kt`（接口 + HTTP impl + 配置）**

```kotlin
package com.ebookreader.simplebook.platform

import com.google.gson.JsonParser
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** OAuth token 端点的可注入抽象（生产=HTTP，测试=fake）。 */
internal interface TokenEndpoint {
    /** 用 authorization code 换 token。返回 null 表示 error（如 invalid_grant）；抛 IOException 表示网络失败。 */
    fun exchange(code: String, codeVerifier: String, redirectUri: String): TokenSet?
    /** 用 refresh_token 换新 token。返回 null 表示 invalid_grant；抛 IOException 表示网络失败。 */
    fun refresh(refreshToken: String): TokenSet?
    /** 取 userinfo email；失败返回 null（不阻断登录）。 */
    fun fetchEmail(accessToken: String): String?
}

internal data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?
)

internal object OAuthConfig {
    const val CLIENT_ID = "347561784963-bk68r08k3c33fnbjc36m4haelsnun1td.apps.googleusercontent.com"
    const val CLIENT_SECRET = "<REDACTED — 实际经 Gradle 从 local.properties 生成 OAuthSecrets.CLIENT_SECRET，不进仓库>"
    const val PORT = 8089
    const val SCOPES =
        "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
}

internal class HttpTokenEndpoint : TokenEndpoint {

    override fun exchange(code: String, codeVerifier: String, redirectUri: String): TokenSet? {
        val params = buildString {
            append("grant_type=authorization_code")
            append("&code=${URLEncoder.encode(code, "UTF-8")}")
            append("&client_id=${URLEncoder.encode(OAuthConfig.CLIENT_ID, "UTF-8")}")
            append("&client_secret=${URLEncoder.encode(OAuthConfig.CLIENT_SECRET, "UTF-8")}")
            append("&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}")
            append("&code_verifier=${URLEncoder.encode(codeVerifier, "UTF-8")}")
        }
        return parseTokenResponse(httpPost(OAuthConfig.TOKEN_ENDPOINT, params))
    }

    override fun refresh(refreshToken: String): TokenSet? {
        val params = buildString {
            append("grant_type=refresh_token")
            append("&refresh_token=${URLEncoder.encode(refreshToken, "UTF-8")}")
            append("&client_id=${URLEncoder.encode(OAuthConfig.CLIENT_ID, "UTF-8")}")
            append("&client_secret=${URLEncoder.encode(OAuthConfig.CLIENT_SECRET, "UTF-8")}")
        }
        return parseTokenResponse(httpPost(OAuthConfig.TOKEN_ENDPOINT, params))
    }

    override fun fetchEmail(accessToken: String): String? {
        val conn = (URL(OAuthConfig.USERINFO_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(body).asJsonObject
            return root.get("email")?.takeIf { !it.isJsonNull }?.asString
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTokenResponse(body: String?): TokenSet? {
        if (body.isNullOrEmpty()) return null
        val root = JsonParser.parseString(body).asJsonObject
        if (root.get("error") != null) return null   // invalid_grant 等
        val access = root.get("access_token")?.takeIf { !it.isJsonNull }?.asString ?: return null
        val refresh = root.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString
        val expiresIn = root.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong
        return TokenSet(access, refresh, expiresIn)
    }

    private fun httpPost(url: String, params: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            OutputStreamWriter(conn.outputStream).use { it.write(params) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            return stream?.bufferedReader()?.use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
```

- [ ] **步骤 2.3：写失败测试 `AuthProviderRefreshTest.kt`**

```kotlin
package com.ebookreader.simplebook.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthProviderRefreshTest {

    /** 可编程的 token 端点 fake：按调用次数返回预设结果。 */
    private class FakeEndpoint : TokenEndpoint {
        val refreshCalls = mutableListOf<String>()
        var refreshResult: TokenSet? = null
        var refreshException: Exception? = null
        override fun exchange(code: String, codeVerifier: String, redirectUri: String): TokenSet? =
            TokenSet("exchange-access", null, 3600)
        override fun refresh(refreshToken: String): TokenSet? {
            refreshCalls += refreshToken
            refreshException?.let { throw it }
            return refreshResult
        }
        override fun fetchEmail(accessToken: String): String? = "user@example.com"
    }

    /** 构造一个内存后端的 AuthProvider，预置 token 状态。 */
    private fun provider(
        endpoint: FakeEndpoint,
        accessToken: String? = null,
        refreshToken: String? = null,
        expiresAt: Long? = null
    ): AuthProvider {
        val store = TokenStorage(InMemorySecretStore(), java.io.File("/tmp/__not_exist__"))
        if (accessToken != null) store.saveTokens(accessToken, refreshToken, expiresAt, "user@example.com")
        return AuthProvider().apply {
            tokenStorage = store
            tokenEndpoint = endpoint
        }
    }

    @Test
    fun getAccessToken_validToken_doesNotRefresh() {
        val ep = FakeEndpoint()
        val p = provider(ep, accessToken = "fresh", refreshToken = "r", expiresAt = System.currentTimeMillis() + 3_600_000)
        assertEquals("fresh", p.getAccessToken())
        assertTrue(ep.refreshCalls.isEmpty(), "未过期不应刷新")
    }

    @Test
    fun getAccessToken_expired_refreshesAndReturnsNewToken() {
        val ep = FakeEndpoint().apply {
            refreshResult = TokenSet("new-access", "new-refresh", 3600)
        }
        val p = provider(ep, accessToken = "stale", refreshToken = "r", expiresAt = System.currentTimeMillis() - 1000)
        assertEquals("new-access", p.getAccessToken())
        assertEquals(listOf("r"), ep.refreshCalls)
        // 刷新后 expiresAt 应更新（远在未来）
        assertTrue(p.getAccessToken() == "new-access")
    }

    @Test
    fun getAccessToken_invalidGrant_throwsAuthExpired() {
        val ep = FakeEndpoint().apply { refreshResult = null }   // invalid_grant
        val p = provider(ep, accessToken = "stale", refreshToken = "r", expiresAt = System.currentTimeMillis() - 1000)
        assertFailsWith<AuthExpiredException> { p.getAccessToken() }
    }

    @Test
    fun getAccessToken_noRefreshToken_throwsAuthExpired() {
        val ep = FakeEndpoint()
        val p = provider(ep, accessToken = "stale", refreshToken = null, expiresAt = System.currentTimeMillis() - 1000)
        assertFailsWith<AuthExpiredException> { p.getAccessToken() }
    }

    @Test
    fun getAccessToken_networkError_propagatesIOException_notAuthExpired() {
        val ep = FakeEndpoint().apply { refreshException = java.io.IOException("network down") }
        val p = provider(ep, accessToken = "stale", refreshToken = "r", expiresAt = System.currentTimeMillis() - 1000)
        assertFailsWith<java.io.IOException> { p.getAccessToken() }
    }

    @Test
    fun getAccessToken_unknownExpiry_triggersRefresh() {
        // expiresAt=null（老 token）→ 视为不新鲜，触发一次刷新拿到 expiresAt
        val ep = FakeEndpoint().apply { refreshResult = TokenSet("refreshed", null, 3600) }
        val p = provider(ep, accessToken = "legacy", refreshToken = "r", expiresAt = null)
        assertEquals("refreshed", p.getAccessToken())
        assertEquals(1, ep.refreshCalls.size)
    }

    @Test
    fun isSignedIn_reflectsStoredAccessToken() {
        val signed = provider(FakeEndpoint(), accessToken = "x", refreshToken = "r", expiresAt = System.currentTimeMillis() + 3_600_000)
        assertTrue(signed.isSignedIn)
        val anon = provider(FakeEndpoint())
        assertFalse(anon.isSignedIn)
    }
}
```

- [ ] **步骤 2.4：运行测试验证失败（AuthProvider 尚无 tokenEndpoint 字段 / 无刷新逻辑）**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.platform.AuthProviderRefreshTest"`
预期：FAIL（编译错误：`tokenEndpoint` unresolved / `getAccessToken` 仍是直接返回 accessToken）。

- [ ] **步骤 2.5：改造 `AuthProvider.kt`（完整新版本）**

```kotlin
package com.ebookreader.simplebook.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

actual class AuthProvider actual constructor() {
    // 懒加载兜底：默认 TokenStorage() 会读真 Keychain + 跑迁移，测试须在首次访问前注入。
    private var _tokenStorage: TokenStorage? = null
    internal var tokenStorage: TokenStorage
        get() = _tokenStorage ?: TokenStorage().also { _tokenStorage = it }
        set(value) { _tokenStorage = value }
    internal var tokenEndpoint: TokenEndpoint = HttpTokenEndpoint()

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt: Long? = null
    private var loaded = false
    private val refreshLock = Any()

    private fun ensureLoaded() {
        if (loaded) return
        val s = tokenStorage
        accessToken = s.getAccessToken()
        refreshToken = s.getRefreshToken()
        expiresAt = s.getExpiresAt()
        loaded = true
    }

    actual val isSignedIn: Boolean
        get() { ensureLoaded(); return accessToken != null }

    actual val userEmail: String?
        get() = tokenStorage.getUserEmail()

    /** 供 DesktopDriveCredential 获取 bearer token。未登录返回 null；过期自动刷新；刷新失败抛 AuthExpiredException。 */
    fun getAccessToken(): String? {
        ensureLoaded()
        if (accessToken == null) return null                 // 未登录
        if (isFresh()) return accessToken
        synchronized(refreshLock) {
            if (isFresh()) return accessToken                // 并发下已被其他线程刷新
            return doRefresh()
        }
    }

    private fun isFresh(): Boolean {
        val exp = expiresAt ?: return false                  // 未知过期时间 → 视为不新鲜，刷新一次以获得 expiresAt
        return System.currentTimeMillis() + SAFETY_MARGIN_MS < exp
    }

    /** 执行刷新。成功返回新 token 并持久化；invalid_grant 抛 AuthExpiredException；网络异常抛 IOException。 */
    private fun doRefresh(): String {
        val current = refreshToken ?: throw AuthExpiredException("no refresh token; re-sign-in required")
        val set = tokenEndpoint.refresh(current)             // null=invalid_grant；IOException 透传
            ?: throw AuthExpiredException("refresh rejected (invalid_grant)")
        accessToken = set.accessToken
        val expiresIn = set.expiresInSeconds ?: 3600L
        expiresAt = System.currentTimeMillis() + expiresIn * 1000L
        set.refreshToken?.let { refreshToken = it }
        tokenStorage.saveTokens(accessToken!!, refreshToken, expiresAt, tokenStorage.getUserEmail())
        return accessToken!!
    }

    actual suspend fun signIn(): Result<String> = withContext(Dispatchers.IO) {
        try {
            logD("AuthProvider", "signIn: starting OAuth PKCE flow")
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)
            val state = generateState()

            val authUrl = buildString {
                append("https://accounts.google.com/o/oauth2/v2/auth?")
                append("client_id=${URLEncoder.encode(OAuthConfig.CLIENT_ID, "UTF-8")}")
                append("&redirect_uri=${URLEncoder.encode("http://localhost:${OAuthConfig.PORT}", "UTF-8")}")
                append("&response_type=code")
                append("&scope=${URLEncoder.encode(OAuthConfig.SCOPES, "UTF-8")}")
                append("&code_challenge=$codeChallenge")
                append("&code_challenge_method=S256")
                append("&state=$state")
                append("&access_type=offline")              // 确保 Google 返回 refresh_token
            }

            val server = DesktopOAuthServer(OAuthConfig.PORT)
            val codeFuture = server.start(state)
            logD("AuthProvider", "signIn: OAuth callback server started on port ${OAuthConfig.PORT}")
            try {
                logD("AuthProvider", "signIn: launching browser for Google auth")
                Desktop.getDesktop().browse(URI(authUrl))
                logD("AuthProvider", "signIn: waiting for callback (5min timeout)")
                val code = codeFuture.get(5, TimeUnit.MINUTES)
                logD("AuthProvider", "signIn: received auth code, exchanging for token")
                exchangeCodeForToken(code, codeVerifier)
            } finally {
                server.stop()
            }
        } catch (e: Exception) {
            logE("AuthProvider", "signIn failed", e)
            Result.failure(e)
        }
    }

    actual fun signOut() {
        accessToken = null
        refreshToken = null
        expiresAt = null
        loaded = true
        tokenStorage.clear()
    }

    private fun exchangeCodeForToken(code: String, codeVerifier: String): Result<String> {
        val set = try {
            tokenEndpoint.exchange(code, codeVerifier, "http://localhost:${OAuthConfig.PORT}")
        } catch (e: Exception) {
            logE("AuthProvider", "token exchange failed", e)
            return Result.failure(e)
        } ?: return Result.failure(Exception("Token exchange failed (no token in response)"))

        val email = runCatching { tokenEndpoint.fetchEmail(set.accessToken) }.getOrNull()
        accessToken = set.accessToken
        refreshToken = set.refreshToken
        expiresAt = System.currentTimeMillis() + (set.expiresInSeconds ?: 3600L) * 1000L
        tokenStorage.saveTokens(accessToken!!, refreshToken, expiresAt, email)
        loaded = true
        return Result.success(accessToken!!)
    }

    companion object {
        private const val SAFETY_MARGIN_MS = 60_000L   // 预留 60s，避免请求途中过期

        private val base64UrlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()
        private val secureRandom = SecureRandom()

        internal fun generateCodeVerifier(): String {
            val bytes = ByteArray(96)
            secureRandom.nextBytes(bytes)
            return base64UrlEncoder.encodeToString(bytes)
        }

        internal fun generateCodeChallenge(verifier: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII))
            return base64UrlEncoder.encodeToString(digest)
        }

        internal fun generateState(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            return base64UrlEncoder.encodeToString(bytes)
        }
    }
}
```

- [ ] **步骤 2.6：运行测试验证通过**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.platform.AuthProviderRefreshTest"`
预期：PASS（7 用例）。

- [ ] **步骤 2.7：确认全量 desktopTest 绿（原 PKCE/State/TokenStorage 测不应受影响）**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest`
预期：PASS。

- [ ] **步骤 2.8：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/AuthExpiredException.kt \
        shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/TokenEndpoint.kt \
        shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/AuthProvider.kt \
        shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/AuthProviderRefreshTest.kt
git commit -m "feat(auth): 桌面 access_token 自动刷新（proactive + AuthExpiredException + access_type=offline）"
```

---

## 任务 3：reauth 跨端抽象与映射（A2）

**文件：**
- 新建：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/ReauthRequest.kt`
- 新建：`shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/ReauthRequest.kt`
- 新建：`shared/src/androidMain/kotlin/com/ebookreader/simplebook/platform/ReauthRequest.kt`
- 删除：`shared/src/commonMain/.../platform/AuthErrors.kt`、`desktopMain/.../AuthErrors.kt`、`androidMain/.../AuthErrors.kt`
- 新建：`shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/ReauthRequestTest.kt`

- [ ] **步骤 3.1：写失败测试 `ReauthRequestTest.kt`**

```kotlin
package com.ebookreader.simplebook.platform

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ReauthRequestTest {
    @Test
    fun authExpiredException_mapsToReauth() {
        assertNotNull(AuthExpiredException().toReauthRequest())
    }

    @Test
    fun genericException_mapsToNull() {
        assertNull(java.io.IOException("net").toReauthRequest())
        assertNull(RuntimeException("boom").toReauthRequest())
    }
}
```

- [ ] **步骤 3.2：运行测试验证失败（`toReauthRequest` 未定义）**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest --tests "com.ebookreader.simplebook.platform.ReauthRequestTest"`
预期：FAIL（unresolved reference: toReauthRequest）。

- [ ] **步骤 3.3：新建 commonMain `ReauthRequest.kt`**

```kotlin
package com.ebookreader.simplebook.platform

/**
 * 「需要重新授权」信号。SyncService 在 catch 到可恢复授权异常时发出，UI 消费。
 * 携带原始 [cause]（Throwable，common 类型）：
 * - 桌面：cause 是 [AuthExpiredException]，UI 直接重走 PKCE 登录（不读 cause）。
 * - Android：cause 是 UserRecoverableAuthIOException，UI 从 cause 窄化取 Intent 启动。
 *
 * 注意：expect 主构造只能用 common 类型（不能引用 android.content.Intent），故用 Throwable 承载。
 */
expect class ReauthRequest(cause: Throwable)

/** 若异常表示「可恢复的授权缺失」（桌面=AuthExpiredException / Android=UserRecoverableAuthIOException），
 *  返回包装该异常的 [ReauthRequest]；否则 null。 */
expect fun Throwable.toReauthRequest(): ReauthRequest?
```

- [ ] **步骤 3.4：新建 desktopMain `ReauthRequest.kt`**

```kotlin
package com.ebookreader.simplebook.platform

actual class ReauthRequest actual constructor(
    val cause: Throwable
)

actual fun Throwable.toReauthRequest(): ReauthRequest? =
    if (this is AuthExpiredException) ReauthRequest(this) else null
```

- [ ] **步骤 3.5：新建 androidMain `ReauthRequest.kt`**

```kotlin
package com.ebookreader.simplebook.platform

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

actual class ReauthRequest actual constructor(
    val cause: Throwable
)

actual fun Throwable.toReauthRequest(): ReauthRequest? {
    return if (this is UserRecoverableAuthIOException) ReauthRequest(this) else null
}
```

> Android 端 `intent` 不在 ReauthRequest 里，而是从 `cause` 现取（见任务 4 步骤 4.6）。

- [ ] **步骤 3.6：删除旧 `AuthErrors.kt`（3 文件，死码）**

```bash
git rm shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/AuthErrors.kt \
       shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/AuthErrors.kt \
       shared/src/androidMain/kotlin/com/ebookreader/simplebook/platform/AuthErrors.kt
```

> 删除后 `SyncService` 仍引用 `isUserRecoverableAuthError` 会编译失败——紧接着步骤 3.7 在**同一任务内**改掉，保证 commit 绿。

- [ ] **步骤 3.7：`SyncService.kt` —— 替换 reauthIntent 字段 + 两处 catch（消除对已删函数的引用）**

import 段：把 `import ...platform.isUserRecoverableAuthError` 改为：
```kotlin
import com.ebookreader.simplebook.platform.ReauthRequest
import com.ebookreader.simplebook.platform.toReauthRequest
```

替换字段（当前 `SyncService.kt:69-72`）：

```kotlin
    private val _reauthRequest = MutableStateFlow<ReauthRequest?>(null)
    val reauthRequest: StateFlow<ReauthRequest?> = _reauthRequest.asStateFlow()

    fun consumeReauthRequest() { _reauthRequest.value = null }
```

`syncAll` catch（当前 `SyncService.kt:109-118`）改为：

```kotlin
            } catch (e: kotlinx.coroutines.CancellationException) {
                logW(TAG, "syncAll: cancelled", e)
                _syncStatus.value = SyncStatus.Error("同步被中断，请重试")
                throw e
            } catch (e: Exception) {
                val req = e.toReauthRequest()
                if (req != null) {
                    logW(TAG, "syncAll: need re-auth")
                    _reauthRequest.value = req
                    _syncStatus.value = SyncStatus.Error("需要重新授权 Google 权限")
                } else {
                    logE(TAG, "syncAll: failed", e)
                    _syncStatus.value = SyncStatus.Error(e.message ?: "同步失败")
                }
            }
```

`importFromDriveFolder` catch（当前 `SyncService.kt:1222-1230`）改为：

```kotlin
        } catch (e: Exception) {
            val req = e.toReauthRequest()
            if (req != null) {
                logW(TAG, "importFromDriveFolder: need re-auth")
                _reauthRequest.value = req
                _importStatus.value = ImportStatus.Error("需要重新授权 Google 权限")
            } else {
                logE(TAG, "importFromDriveFolder: failed", e)
                _importStatus.value = ImportStatus.Error(e.message ?: "导入失败")
            }
        }
```

- [ ] **步骤 3.8：编译 + ReauthRequestTest 验证通过**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:compileKotlinDesktop :shared:compileKotlinAndroid :shared:desktopTest --tests "com.ebookreader.simplebook.platform.ReauthRequestTest"`
预期：BUILD SUCCESSFUL + ReauthRequestTest PASS（确认无 `isUserRecoverableAuthError` 残留引用、SyncService 编译通过）。

- [ ] **步骤 3.9：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/platform/ReauthRequest.kt \
        shared/src/desktopMain/kotlin/com/ebookreader/simplebook/platform/ReauthRequest.kt \
        shared/src/androidMain/kotlin/com/ebookreader/simplebook/platform/ReauthRequest.kt \
        shared/src/desktopTest/kotlin/com/ebookreader/simplebook/platform/ReauthRequestTest.kt \
        shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/SyncService.kt
git commit -m "feat(auth): reauth 跨端抽象 + SyncService 接入（expect ReauthRequest/toReauthRequest，删 AuthErrors 死码）"
```

---

## 任务 4：reauth 接线 ViewModel → UI（A2）

**文件：**
- 改造：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/sync/SyncViewModel.kt`
- 改造：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt`
- 改造：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/settings/SettingsScreen.kt`
- 改造：`shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt`
- 改造：`androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt`

- [ ] **步骤 4.1：`SyncViewModel.kt` —— 暴露 reauthRequest**

在 `SyncViewModel` 中（`lastSyncedAt` 暴露之后）增加：

```kotlin
    val reauthRequest: StateFlow<ReauthRequest?> = syncService.reauthRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun consumeReauthRequest() {
        syncService.consumeReauthRequest()
    }
```

并补 import：`import com.ebookreader.simplebook.platform.ReauthRequest`。

- [ ] **步骤 4.2：`SimpleBookNavHost.kt` —— 透传 onReauthClick**

参数列表（当前 `SimpleBookNavHost.kt:24-31`）增加 `onReauthClick`：

```kotlin
@Composable
fun SimpleBookNavHost(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    syncViewModel: SyncViewModel? = null,
    signInLauncher: (() -> Unit)? = null,
    onReauthClick: ((ReauthRequest) -> Unit)? = null,
    onImportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

Settings 目标（当前 `SimpleBookNavHost.kt:106-112`）传入：

```kotlin
        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                onSignInClick = signInLauncher,
                onReauthClick = onReauthClick,
                syncViewModel = svm
            )
        }
```

补 import：`import com.ebookreader.simplebook.platform.ReauthRequest`。

- [ ] **步骤 4.3：`SettingsScreen.kt` —— 收集 reauth + 重新登录对话框**

参数（当前 `SettingsScreen.kt:71-76`）增加 `onReauthClick`：

```kotlin
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onSignInClick: (() -> Unit)? = null,
    onReauthClick: ((ReauthRequest) -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel()
)
```

删除现有注释块（当前 `SettingsScreen.kt:89-101` 的 TODO reauthLauncher 注释），改为：

```kotlin
    val reauthRequest by syncViewModel.reauthRequest.collectAsState()
```

补 import：`import com.ebookreader.simplebook.platform.ReauthRequest`、`import androidx.compose.material3.AlertDialog`。

在 `Scaffold { ... }` 的 `Column` 顶部（`// ── 字体大小 ──` 之前，当前约 `SettingsScreen.kt:124`）插入对话框：

```kotlin
            reauthRequest?.let { req ->
                AlertDialog(
                    onDismissRequest = { syncViewModel.consumeReauthRequest() },
                    title = { Text("需要重新授权") },
                    text = { Text("Google 授权已失效，请重新登录后重试同步。") },
                    confirmButton = {
                        TextButton(onClick = {
                            syncViewModel.consumeReauthRequest()
                            if (onReauthClick != null) onReauthClick.invoke(req)
                            else onSignInClick?.invoke()
                        }) { Text("重新登录") }
                    },
                    dismissButton = {
                        TextButton(onClick = { syncViewModel.consumeReauthRequest() }) {
                            Text(strings.cancel)
                        }
                    }
                )
            }
```

> 桌面端 `onReauthClick=null` → 走 `onSignInClick`（PKCE 重登）。Android 端 `onReauthClick` 由 MainActivity 提供。

- [ ] **步骤 4.4：`App.kt` —— 桌面 onReauthClick**

`SimpleBookNavHost(...)` 调用处（当前 `App.kt:77-82`）增加：

```kotlin
                SimpleBookNavHost(
                    navController = navController,
                    windowSizeClass = windowSizeClass,
                    syncViewModel = syncViewModel,
                    signInLauncher = { syncViewModel.signIn() },
                    onReauthClick = { syncViewModel.signIn() }
                )
```

> 桌面 `ReauthRequest` 无 payload，重登即 `syncViewModel.signIn()`。

- [ ] **步骤 4.5：`MainActivity.kt` —— Android onReauthClick**

`SimpleBookNavHost(...)` 调用处（当前 `MainActivity.kt:183-191`）增加 `onReauthClick`：

```kotlin
                            SimpleBookNavHost(
                                navController = navController,
                                windowSizeClass = windowSizeClass,
                                syncViewModel = syncViewModel,
                                signInLauncher = { googleSignInLauncher.launch(authManager.signInIntent) },
                                onReauthClick = { req ->
                                    // cause 是 UserRecoverableAuthIOException（toReauthRequest 仅在该类型时非 null）
                                    val intent = (req.cause as? com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException)?.intent
                                    if (intent != null) googleSignInLauncher.launch(intent)
                                },
                                onImportClick = {
                                    importLauncher.launch(arrayOf("application/epub+zip", "text/plain"))
                                }
                            )
```

> 复用现有 `googleSignInLauncher`（`StartActivityForResult` 契约，回调 `handleSignInResult` 对 re-consent 结果同样适用）。Intent 从 `req.cause` 窄化获取（commonMain 不能直接持有 Intent，故 ReauthRequest 承载 Throwable）。

- [ ] **步骤 4.6：编译验证（commonMain + desktop + android）**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:compileKotlinDesktop :androidApp:assembleDebug`
预期：BUILD SUCCESSFUL（`ReauthRequest` import 齐全、UI 接线无类型错误）。

- [ ] **步骤 4.7：确认 desktopTest 全绿**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest`
预期：PASS（任务 1/2/3 测 + 原 23 测全过）。

- [ ] **步骤 4.8：Commit**

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/sync/SyncViewModel.kt \
        shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt \
        shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/settings/SettingsScreen.kt \
        shared/src/commonMain/kotlin/com/ebookreader/simplebook/App.kt \
        androidApp/src/main/kotlin/com/ebookreader/simplebook/MainActivity.kt
git commit -m "feat(sync): 接通 reauth UI（ViewModel→NavHost→SettingsScreen，桌面重登/Android Intent）"
```

---

## 任务 5：Drive SSL 定位与修复（A4）

**性质：** bug 诊断，无单测，靠手动 run + 栈分析。前置：任务 1-4 完成（排除 token 因素干扰）。

**文件：** 视诊断结果而定（最可能 `shared/src/commonMain/.../data/remote/GoogleDriveClient.kt`）。

- [ ] **步骤 5.1：复现并抓栈**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :desktopApp:run`
操作：登录 → 书架触发同步（或设置页「立即同步」）。
观察 stderr/log：抓 `SSLHandshakeException` / `SSL peer shut down incorrectly` / `Remote host terminated the handshake` 的**完整堆栈**，记录：
- 异常从 `AuthProvider`（token/userinfo 的 `HttpURLConnection`）还是 `GoogleDriveClient`（`NetHttpTransport`/`.execute()`）抛出？
- 目标 host（`oauth2.googleapis.com` / `www.googleapis.com` / `drive.googleapis.com`）？
- 是否每次必现，还是偶发（VPN/瞬时）？

- [ ] **步骤 5.2：按栈走决策树，落最小修复**

根据步骤 5.1 结论选一：

**① 若来自 `GoogleDriveClient` 且偶发（最可能：VPN/瞬时断连）** → 给 `GoogleDriveClient` 的 `.execute()` 调用包重试。在 `GoogleDriveClient` 顶部加私有扩展：

```kotlin
private inline fun <T> retryIo(default: T? = null, times: Int = 3, body: () -> T): T? {
    var delayMs = 500L
    repeat(times) { attempt ->
        try {
            return body()
        } catch (e: javax.net.ssl.SSLException) {
            logW("GoogleDriveClient", "SSL error attempt ${attempt + 1}/$times: ${e.message}")
            if (attempt == times - 1) throw e
        } catch (e: java.io.IOException) {
            logW("GoogleDriveClient", "IO error attempt ${attempt + 1}/$times: ${e.message}")
            if (attempt == times - 1) throw e
        }
        Thread.sleep(delayMs); delayMs *= 2
    }
    return default
}
```

把关键调用点（如 `drive.files().list()...execute()`、`executeMediaAndDownloadTo`）改为 `retryIo { ...execute() }`。（范围按实际失败的操作精准包，不全量改。）

**② 若来自 `AuthProvider.httpPost`/`HttpURLConnection` 且偶发** → 同理给 `HttpTokenEndpoint.httpPost` / `fetchEmail` 加重试，或确认是 token 过期误判（A1 已修）。

**③ 若报 TLS 版本** → `GoogleDriveClient` 的 `NetHttpTransport()` 改为显式 TLSv1.3。用正规 `SSLContext` 配置（**不要**用 `doNotValidateCertificate()`，那是绕过校验、不安全）：

```kotlin
import java.security.SecureRandom
import javax.net.ssl.SSLContext
// ...
val ssl = SSLContext.getInstance("TLSv1.3").apply { init(null, null, SecureRandom()) }
com.google.api.client.http.javanet.NetHttpTransport.Builder()
    .setSslSocketFactory(ssl.socketFactory)
    .build()
```

（仅在确证是 TLS 版本问题时才走此分支；现代 JDK 默认已协商 TLSv1.3，此分支概率低。）

**④ 若实为 401 误读** → A1 完成后应自愈；重新触发确认。

- [ ] **步骤 5.3：验证修复**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :desktopApp:run`
操作：正常网络 + VPN 两种环境各触发同步一次。
预期：同步成功（`SyncStatus.Success`，无 SSL 异常）。

- [ ] **步骤 5.4：记录结论 + Commit**

提交信息注明根因与落点，例如：

```bash
git add shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/remote/GoogleDriveClient.kt
git commit -m "fix(drive): SSL 偶发握手失败加重试（VPN/瞬时断连，指数退避×3）"
```

并在 `[kmp-desktop-migration]` 记忆「遗留项」里勾掉 A4、记录真因。

---

## 收尾验证（全部任务完成后）

- [ ] **V1：全量 desktopTest**
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :shared:desktopTest` → 全绿（原 23 + TokenStorage 7 + AuthProviderRefresh 7 + ReauthRequest 2 ≈ 39）。
- [ ] **V2：Android 编译**
  `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL。
- [ ] **V3：桌面端实测（端到端）**
  `./gradlew :desktopApp:run`：
  1. 登录成功 → 同步成功。
  2. 手动构造 token 过期：运行中改 Keychain `tokens` 条目的 `expiresAt` 为已过去时间戳（或等 1h）→ 触发同步 → 确认**自动刷新** + 同步成功（log 无 401/SSL）。
  3. 删除 Keychain `tokens` 条目（模拟 refresh 失效）→ 触发同步 → 确认**弹出「需要重新授权」对话框** → 「重新登录」→ PKCE 流程 → 恢复。
  4. 旧 `~/Library/SimpleBook/tokens.properties` 若存在：首次启动应被迁入 Keychain 并删除文件（`security find-generic-password -s com.ebookreader.simplebook -a tokens -w` 非空）。
- [ ] **V4：Android 真机实测（reauth）**
  `./gradlew :androidApp:installDebug` → 真机：触发需 reauth 场景（如清除 scope 授权）→ 确认 reauth Intent 启动 → 重授权 → 同步恢复。

## 范围之外
- Android **release 签名**（独立 Spec B，另出计划）。
- Drive 401 反应式重试（proactive 刷新已覆盖常规场景）。
- Keychain `-w` argv 可见性的额外加固（shredding/进程隔离）。
- 老用户首次过期无 refresh_token 的自动静默重登（按设计需手动重登一次）。
