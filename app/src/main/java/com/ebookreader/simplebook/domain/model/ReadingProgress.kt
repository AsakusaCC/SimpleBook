package com.ebookreader.simplebook.domain.model

data class ReadingProgress(
    val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val percentage: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis()
)
