package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkService @Inject constructor(
    private val bookmarkRepo: BookmarkRepository
) {
    fun getBookmarksForBook(bookUuid: String): Flow<List<Bookmark>> =
        bookmarkRepo.getBookmarksForBook(bookUuid)

    fun getAllBookmarks(): Flow<List<Bookmark>> = bookmarkRepo.getAllBookmarks()

    suspend fun addBookmark(bookUuid: String, chapterIndex: Int, charOffset: Long, name: String) {
        bookmarkRepo.addBookmark(
            Bookmark(bookUuid = bookUuid, chapterIndex = chapterIndex, charOffset = charOffset, name = name)
        )
    }

    suspend fun deleteBookmarkForPosition(bookUuid: String, chapterIndex: Int) {
        val bookmarks = bookmarkRepo.getBookmarksForBook(bookUuid).first()
        val match = bookmarks.find { it.chapterIndex == chapterIndex }
        if (match != null) {
            bookmarkRepo.softDeleteBookmark(match.uuid)
        }
    }

    suspend fun softDeleteBookmark(bookmark: Bookmark) {
        bookmarkRepo.softDeleteBookmark(bookmark.uuid)
    }

    suspend fun isBookmarked(bookUuid: String, chapterIndex: Int): Boolean {
        val bookmarks = bookmarkRepo.getBookmarksForBook(bookUuid).first()
        return bookmarks.any { it.chapterIndex == chapterIndex }
    }
}
