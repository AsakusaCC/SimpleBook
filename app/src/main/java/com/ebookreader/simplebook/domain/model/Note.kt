package com.ebookreader.simplebook.domain.model

data class Note(
    val id: Long = 0,
    val bookId: Long,
    val highlightId: Long? = null,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
