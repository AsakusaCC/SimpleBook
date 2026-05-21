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
    fun getBookmarksForBook(bookId: Long): Flow<List<Bookmark>> =
        bookmarkRepo.getBookmarksForBook(bookId)

    fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkRepo.getAllBookmarks()

    suspend fun addBookmark(bookId: Long, chapterIndex: Int, charOffset: Long, name: String): Long =
        bookmarkRepo.addBookmark(
            Bookmark(
                bookId = bookId,
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                name = name
            )
        )

    suspend fun deleteBookmarkForPosition(bookId: Long, chapterIndex: Int) {
        val bookmarks = bookmarkRepo.getBookmarksForBook(bookId).first()
        val match = bookmarks.find { it.chapterIndex == chapterIndex }
        if (match != null) {
            bookmarkRepo.deleteBookmark(match)
        }
    }

    suspend fun isBookmarked(bookId: Long, chapterIndex: Int): Boolean {
        val bookmarks = bookmarkRepo.getBookmarksForBook(bookId).first()
        return bookmarks.any { it.chapterIndex == chapterIndex }
    }
}
