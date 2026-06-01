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
    fun getBookmarksForBook(bookUuid: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForBook(bookUuid).map { list -> list.map { it.toDomain() } }

    suspend fun getBookmarksForBookNow(bookUuid: String): List<Bookmark> =
        bookmarkDao.getBookmarksForBookNow(bookUuid).map { it.toDomain() }

    suspend fun getAllBookmarksForBookNow(bookUuid: String): List<Bookmark> =
        bookmarkDao.getAllBookmarksForBookNow(bookUuid).map { it.toDomain() }

    fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.getAllBookmarks().map { list -> list.map { it.toDomain() } }

    suspend fun addBookmark(bookmark: Bookmark) { bookmarkDao.insert(bookmark.toEntity()) }

    suspend fun softDeleteBookmark(uuid: String) { bookmarkDao.softDelete(uuid) }

    suspend fun hardDeleteByBook(bookUuid: String) { bookmarkDao.hardDeleteByBook(bookUuid) }

    private fun BookmarkEntity.toDomain() = Bookmark(
        uuid = uuid, bookUuid = bookUuid, chapterIndex = chapterIndex,
        charOffset = charOffset, name = name, createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )

    private fun Bookmark.toEntity() = BookmarkEntity(
        uuid = uuid, bookUuid = bookUuid, chapterIndex = chapterIndex,
        charOffset = charOffset, name = name, createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )
}
