package com.ebookreader.simplebook.domain.model

data class Folder(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null
)
