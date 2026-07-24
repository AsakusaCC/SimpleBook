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
        assertTrue(p.getAccessToken() == "new-access")
        assertEquals(1, ep.refreshCalls.size)   // 刷新后 expiresAt 已更新，第二次调用不应再次刷新
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
