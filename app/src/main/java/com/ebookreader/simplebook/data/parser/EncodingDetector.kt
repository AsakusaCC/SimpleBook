package com.ebookreader.simplebook.data.parser

import org.mozilla.universalchardet.UniversalDetector
import java.io.File
import java.nio.charset.Charset

class EncodingDetector {
    fun detectEncoding(file: File): Charset {
        val buf = ByteArray(8192)
        val detector = UniversalDetector(null)
        file.inputStream().use { input ->
            var bytesRead: Int
            while (input.read(buf).also { bytesRead = it } > 0 && !detector.isDone) {
                detector.handleData(buf, 0, bytesRead)
            }
        }
        detector.dataEnd()
        val detectedCharset = detector.detectedCharset
        return if (detectedCharset != null) {
            try { Charset.forName(detectedCharset) } catch (e: Exception) { Charsets.UTF_8 }
        } else {
            Charsets.UTF_8
        }
    }
}
