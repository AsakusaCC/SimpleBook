package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int = 0xFFFFFF00.toInt(), // ARGB
    val note: String? = null,
    val createdAt: Long,
    val syncVersion: Long = 1,
    val lastSyncedAt: Long? = null
)
