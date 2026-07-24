package com.ebookreader.simplebook.domain.model

data class Highlight(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val startOffset: Long,
    val endOffset: Long,
    val color: Long = 0xFFFFFF00,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)