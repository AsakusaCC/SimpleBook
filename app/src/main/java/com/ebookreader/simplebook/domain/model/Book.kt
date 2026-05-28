package com.ebookreader.simplebook.domain.model

data class Book(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val filePath: String,
    val format: BookFormat,
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val folderId: String? = null,
    val driveFileId: String? = null
)