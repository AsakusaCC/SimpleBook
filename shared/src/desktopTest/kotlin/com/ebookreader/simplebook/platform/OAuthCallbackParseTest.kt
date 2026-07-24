package com.ebookreader.simplebook.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuthCallbackParseTest {

    @Test
    fun extractsCodeFromTypicalCallbackQuery() {
        val query = "code=4/0AX4XfWgC123abc&scope=drive%20drive.appdata&state=xyz"
        assertEquals("4/0AX4XfWgC123abc", parseAuthorizationCode(query))
    }

    @Test
    fun extractsCodeWhenCodeIsLastParam() {
        val query = "state=abc&scope=drive&code=THE_CODE_VALUE"
        assertEquals("THE_CODE_VALUE", parseAuthorizationCode(query))
    }

    @Test
    fun returnsNullWhenNoCodePresent() {
        // 用户拒绝授权等错误响应：error=access_denied&state=xyz
        assertNull(parseAuthorizationCode("error=access_denied&state=xyz"))
    }

    @Test
    fun returnsNullForNullOrEmptyQuery() {
        assertNull(parseAuthorizationCode(null))
        assertNull(parseAuthorizationCode(""))
    }

    @Test
    fun handlesCodeValueContainingEqualsSign() {
        // limit=2 切分：值为 "a=b" 的 code
        val query = "state=s&code=a=b"
        assertEquals("a=b", parseAuthorizationCode(query))
    }

    @Test
    fun ignoresParamsNamedSimilarButNotCode() {
        assertNull(parseAuthorizationCode("authcode=123&state=s"))
    }
}
