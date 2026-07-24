package com.ebookreader.simplebook.domain.model

data class Bookmark(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)