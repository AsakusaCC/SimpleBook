package com.ebookreader.simplebook.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthProviderPkceTest {

    @Test
    fun codeVerifier_isValidBase64UrlWithoutPadding() {
        val verifier = AuthProvider.generateCodeVerifier()
        // Base64URL 编码 96 字节 -> ceil(96/3)*4 = 128 字符，无 '=' padding
        assertEquals(128, verifier.length, "verifier length should be 128 for 96 bytes base64url")
        assertTrue(!verifier.contains('='), "verifier must not contain padding '='")
        // 合法 base64url 字符集
        assertTrue(
            verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' },
            "verifier contains invalid base64url char"
        )
    }

    @Test
    fun codeVerifier_isRandomAcrossCalls() {
        val a = AuthProvider.generateCodeVerifier()
        val b = AuthProvider.generateCodeVerifier()
        assertNotEquals(a, b, "two verifiers should differ")
    }

    @Test
    fun codeChallenge_isDeterministicForGivenVerifier() {
        // RFC 7636 附录 B 的官方测试向量
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val expectedChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
        val actual = AuthProvider.generateCodeChallenge(verifier)
        assertEquals(expectedChallenge, actual, "S256 challenge must match RFC 7636 test vector")
    }

    @Test
    fun codeChallenge_is43CharsBase64UrlForSha256() {
        // SHA-256 输出 32 字节 -> Base64URL 无 padding -> ceil(32/3)*4 去尾 = 43 字符
        val challenge = AuthProvider.generateCodeChallenge(AuthProvider.generateCodeVerifier())
        assertEquals(43, challenge.length, "S256 challenge must be 43 chars")
        assertTrue(!challenge.contains('='), "challenge must not contain padding")
    }

    @Test
    fun state_isValidBase64UrlWithoutPadding() {
        val state = AuthProvider.generateState()
        // 16 字节 -> Base64URL 无 padding = ceil(16/3)*4 去尾 = 22 字符
        assertEquals(22, state.length, "state length should be 22 for 16 bytes")
        assertTrue(!state.contains('='), "state must not contain padding")
        assertNotEquals(AuthProvider.generateState(), state, "state should be random")
    }
}
