package com.ebookreader.simplebook.domain.model

data class SyncLog(
    val id: Long = 0,
    val entityType: String,
    val entityUuid: String,
    val action: String,
    val localUpdatedAt: Long?,
    val remoteUpdatedAt: Long?,
    val resolvedAt: Long = System.currentTimeMillis(),
    val bookUuid: String
)