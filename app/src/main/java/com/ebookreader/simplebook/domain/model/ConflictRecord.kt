package com.ebookreader.simplebook.domain.model

data class ConflictRecord(
    val id: Long = 0,
    val bookId: Long,
    val entityType: String,
    val entityId: Long,
    val localSyncVersion: Long,
    val remoteSyncVersion: Long,
    val localData: String,
    val remoteData: String,
    val createdAt: Long,
    val resolvedAt: Long? = null
)
