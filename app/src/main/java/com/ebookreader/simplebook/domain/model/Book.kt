package com.ebookreader.simplebook.domain.model

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val filePath: String,
    val format: BookFormat,
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null
)
