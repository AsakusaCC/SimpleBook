package com.ebookreader.simplebook.domain.model

enum class ChapterType {
    EPUB_HTML, TXT_PLAIN
}

data class Chapter(
    val index: Int,
    val title: String,
    val content: String,
    val type: ChapterType
)
