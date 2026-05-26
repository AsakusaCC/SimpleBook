package com.ebookreader.simplebook.domain.model

data class ReadingProgress(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val percentage: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)