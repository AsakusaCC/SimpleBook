package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "sync_logs",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUuid: String,
    val entityType: String,
    val entityUuid: String,
    val action: String,
    val localUpdatedAt: Long?,
    val remoteUpdatedAt: Long?,
    val resolvedAt: Long
)