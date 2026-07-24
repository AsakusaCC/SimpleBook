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
    @Volatile
    private var _tokenStorage: TokenStorage? = null
    internal var tokenStorage: TokenStorage
        get() = _tokenStorage ?: TokenStorage().also { _tokenStorage = it }
        set(value) { _tokenStorage = value }
    internal var tokenEndpoint: TokenEndpoint = HttpTokenEndpoint()

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var expiresAt: Long? = null
    private var _userEmail: String? = null
    @Volatile
    private var loaded = false
    private val refreshLock = Any()

    private fun ensureLoaded() {
        synchronized(refreshLock) {
            if (loaded) return                  // 块内 return 释放锁后退出，正确
            val s = tokenStorage
            accessToken = s.getAccessToken()
            refreshToken = s.getRefreshToken()
            expiresAt = s.getExpiresAt()
            _userEmail = s.getUserEmail()
            loaded = true
        }
    }

    actual val isSignedIn: Boolean
        get() { ensureLoaded(); return accessToken != null }

    actual val userEmail: String?
        get() { ensureLoaded(); return _userEmail }

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
        tokenStorage.saveTokens(accessToken!!, refreshToken, expiresAt, _userEmail)
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
        _userEmail = null
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
        _userEmail = email
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
