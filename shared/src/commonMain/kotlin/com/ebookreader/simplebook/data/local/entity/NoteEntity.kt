package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["bookUuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HighlightEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["highlightUuid"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("bookUuid"), Index("highlightUuid")]
)
data class NoteEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val highlightUuid: String? = null,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)