package com.ebookreader.simplebook.domain.model

data class Bookmark(
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
