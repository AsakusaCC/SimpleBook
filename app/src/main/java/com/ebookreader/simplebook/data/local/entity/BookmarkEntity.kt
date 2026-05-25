package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val name: String = "",
    val createdAt: Long,
    val syncVersion: Long = 1,
    val lastSyncedAt: Long? = null
)
