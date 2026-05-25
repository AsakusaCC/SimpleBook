package com.ebookreader.simplebook.data.remote

data class SyncManifest(
    val books: Map<String, String> = emptyMap()
)

data class BookMetadata(
    val bookId: Long,
    val title: String,
    val author: String,
    val format: String,
    val fileSize: Long = 0,
    val coverPath: String? = null,
    val syncVersion: Long = 1,
    val updatedAt: Long,
    val progress: ProgressMetadata? = null,
    val bookmarks: List<BookmarkMetadata> = emptyList(),
    val highlights: List<HighlightMetadata> = emptyList(),
    val notes: List<NoteMetadata> = emptyList()
)

data class ProgressMetadata(
    val chapterIndex: Int,
    val charOffset: Long,
    val percentage: Double,
    val syncVersion: Long,
    val updatedAt: Long
)

data class BookmarkMetadata(
    val chapterIndex: Int,
    val charOffset: Long,
    val name: String,
    val syncVersion: Long,
    val createdAt: Long
)

data class HighlightMetadata(
    val chapterIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int,
    val note: String?,
    val syncVersion: Long,
    val createdAt: Long
)

data class NoteMetadata(
    val highlightId: Long?,
    val chapterIndex: Int,
    val charOffset: Long,
    val content: String,
    val syncVersion: Long,
    val createdAt: Long
)
