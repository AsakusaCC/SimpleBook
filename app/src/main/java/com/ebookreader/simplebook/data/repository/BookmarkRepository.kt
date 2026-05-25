package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import com.ebookreader.simplebook.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) {
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForBook(bookId).map { list -> list.map { it.toDomain() } }

    fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.getAllBookmarks().map { list -> list.map { it.toDomain() } }

    suspend fun addBookmark(bookmark: Bookmark): Long =
        bookmarkDao.insert(bookmark.toEntity())

    suspend fun deleteBookmark(bookmark: Bookmark) =
        bookmarkDao.delete(bookmark.toEntity())

    private fun BookmarkEntity.toDomain() = Bookmark(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        name = name,
        createdAt = createdAt,
        syncVersion = syncVersion,
        lastSyncedAt = lastSyncedAt
    )

    private fun Bookmark.toEntity() = BookmarkEntity(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        name = name,
        createdAt = createdAt,
        syncVersion = syncVersion,
        lastSyncedAt = lastSyncedAt
    )
}
