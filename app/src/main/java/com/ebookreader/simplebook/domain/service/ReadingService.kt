package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.ReadingProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingService @Inject constructor(
    private val readingProgressRepo: ReadingProgressRepository,
    private val bookRepo: BookRepository
) {
    suspend fun saveProgress(bookId: Long, chapterIndex: Int, charOffset: Long, percentage: Double) {
        val existing = readingProgressRepo.getProgress(bookId)
        val progress = if (existing != null) {
            existing.copy(
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                percentage = percentage,
                updatedAt = System.currentTimeMillis()
            )
        } else {
            ReadingProgress(
                bookId = bookId,
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                percentage = percentage,
                updatedAt = System.currentTimeMillis()
            )
        }
        readingProgressRepo.saveProgress(progress)

        // Update lastReadAt on book
        bookRepo.getBookById(bookId)?.let { book ->
            bookRepo.updateBook(book.copy(lastReadAt = System.currentTimeMillis()))
        }
    }

    suspend fun loadProgress(bookId: Long): ReadingProgress? =
        readingProgressRepo.getProgress(bookId)
}
