package com.ebookreader.simplebook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val uuid: String,
    val title: String,
    val author: String = "",
    val filePath: String,
    @ColumnInfo(name = "format") val format: String,
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long,
    val lastReadAt: Long? = null,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null,
    val folderId: String? = null
)