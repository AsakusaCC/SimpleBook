package com.ebookreader.simplebook.data.parser

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TxtParser @Inject constructor() {
    private val encodingDetector = EncodingDetector()
    private val chapterSplitter = ChapterSplitter()

    fun parse(file: File): TxtParseResult {
        // 1. Detect encoding
        val charset = encodingDetector.detectEncoding(file)

        // 2. Read file with detected encoding
        val text = file.readText(charset)

        // 3. Split into chapters
        val chapters = chapterSplitter.split(text)

        return TxtParseResult(
            title = file.nameWithoutExtension,
            chapters = chapters,
            encoding = charset.name()
        )
    }
}
