package com.ebookreader.simplebook.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OAuthStateValidationTest {

    @Test
    fun extractsStateFromTypicalCallbackQuery() {
        val query = "code=4/0AX4XfWgC123abc&scope=drive%20drive.appdata&state=THE_STATE_VALUE"
        assertEquals("THE_STATE_VALUE", parseState(query))
    }

    @Test
    fun extractsStateWhenStateIsFirstParam() {
        val query = "state=abc123&scope=drive&code=THE_CODE_VALUE"
        assertEquals("abc123", parseState(query))
    }

    @Test
    fun returnsNullWhenNoStatePresent() {
        // 缺失 state 参数的回调（异常情况）
        assertNull(parseState("code=abc&scope=drive"))
        assertNull(parseState("error=access_denied"))
    }

    @Test
    fun returnsNullForNullOrEmptyQuery() {
        assertNull(parseState(null))
        assertNull(parseState(""))
    }

    @Test
    fun handlesStateValueContainingEqualsSign() {
        // limit=2 切分：值为 "a=b=c" 的 state
        val query = "code=c&state=a=b=c"
        assertEquals("a=b=c", parseState(query))
    }

    @Test
    fun ignoresParamsNamedSimilarButNotState() {
        // 不误匹配 mytate / upstreamstate 等
        assertNull(parseState("mystate=123&upstreamstate=456&code=c"))
    }
}
