package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class ReadingProgressEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val percentage: Double = 0.0,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)