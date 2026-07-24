package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class HighlightEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int = 0xFFFFFF00.toInt(),
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)