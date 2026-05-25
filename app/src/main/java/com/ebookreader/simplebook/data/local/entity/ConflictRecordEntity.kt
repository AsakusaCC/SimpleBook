package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "conflict_records",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ConflictRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
