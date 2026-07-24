package com.ebookreader.simplebook.ui

import coil3.PlatformContext
import coil3.request.Options
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalEncodingApi::class)
class DataUriMapperTest {

    private val options = Options(PlatformContext.INSTANCE)
    private val mapper = DataUriMapper()

    @Test
    fun mapsBase64DataUriToDecodedBytes() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val dataUri = "data:image/jpeg;base64,${Base64.encode(original)}"
        val result = mapper.map(dataUri, options)
        assertNotNull(result)
        assertContentEquals(original, result)
    }

    @Test
    fun returnsNullForHttpUri() {
        assertNull(mapper.map("https://example.com/x.jpg", options))
    }

    @Test
    fun returnsNullForFileUri() {
        assertNull(mapper.map("file:///tmp/x.jpg", options))
    }

    @Test
    fun returnsNullForNonBase64DataUri() {
        assertNull(mapper.map("data:text/plain,hello", options))
    }

    @Test
    fun handlesPngAndSvgMimeTypes() {
        val png = "data:image/png;base64,${Base64.encode(byteArrayOf(0, 0))}"
        val svg = "data:image/svg+xml;base64,${Base64.encode("<svg/>".encodeToByteArray())}"
        assertNotNull(mapper.map(png, options))
        assertNotNull(mapper.map(svg, options))
    }

    @Test
    fun isCaseInsensitiveOnPrefixAndBase64Marker() {
        val original = byteArrayOf(9, 9)
        val dataUri = "DATA:image/jpeg;BASE64,${Base64.encode(original)}"
        val result = mapper.map(dataUri, options)
        assertNotNull(result)
        assertContentEquals(original, result)
    }
}
