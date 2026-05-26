package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class BookmarkEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val name: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)