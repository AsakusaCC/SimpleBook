package com.ebookreader.simplebook.data.remote

data class BookMetadata(
    val version: Int = 3,
    val bookUuid: String,
    val title: String,
    val author: String,
    val format: String,
    val fileSize: Long = 0,
    val coverPath: String? = null,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val progress: ProgressMetadata? = null,
    val bookmarks: List<BookmarkMetadata> = emptyList(),
    val highlights: List<HighlightMetadata> = emptyList(),
    val notes: List<NoteMetadata> = emptyList()
)

data class ProgressMetadata(
    val uuid: String,
    val chapterIndex: Int,
    val charOffset: Long,
    val percentage: Double,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class BookmarkMetadata(
    val uuid: String,
    val chapterIndex: Int,
    val charOffset: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class HighlightMetadata(
    val uuid: String,
    val chapterIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class NoteMetadata(
    val uuid: String,
    val highlightUuid: String? = null,
    val chapterIndex: Int,
    val charOffset: Long,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)