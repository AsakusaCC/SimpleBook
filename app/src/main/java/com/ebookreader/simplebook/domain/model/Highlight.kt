package com.ebookreader.simplebook.domain.model

data class Highlight(
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val startOffset: Long,
    val endOffset: Long,
    val color: Long = 0xFFFFFF00,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
