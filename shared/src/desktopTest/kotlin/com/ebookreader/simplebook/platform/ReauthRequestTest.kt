package com.ebookreader.simplebook.platform

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class ReauthRequestTest {
    @Test
    fun authExpiredException_mapsToReauthAndCarriesCause() {
        val ex = AuthExpiredException()
        val req = ex.toReauthRequest()
        assertNotNull(req)
        assertSame(ex, req!!.cause)
    }

    @Test
    fun genericException_mapsToNull() {
        assertNull(java.io.IOException("net").toReauthRequest())
        assertNull(RuntimeException("boom").toReauthRequest())
    }
}
