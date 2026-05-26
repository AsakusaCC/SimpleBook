package com.ebookreader.simplebook.domain.model

data class Note(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val highlightUuid: String? = null,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)