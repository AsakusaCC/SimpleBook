package com.ebookreader.simplebook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val author: String = "",
    val filePath: String,
    @ColumnInfo(name = "format") val format: String, // "EPUB" or "TXT"
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long, // timestamp millis
    val lastReadAt: Long? = null
)
