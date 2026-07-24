package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.ReadingProgress

class ReadingService constructor(
    private val readingProgressRepo: ReadingProgressRepository,
    private val bookRepo: BookRepository
) {
    suspend fun saveProgress(bookUuid: String, chapterIndex: Int, charOffset: Long, percentage: Double) {
        val existing = readingProgressRepo.getProgress(bookUuid)
        val now = System.currentTimeMillis()
        val progress = if (existing != null) {
            existing.copy(chapterIndex = chapterIndex, charOffset = charOffset, percentage = percentage, updatedAt = now)
        } else {
            ReadingProgress(bookUuid = bookUuid, chapterIndex = chapterIndex, charOffset = charOffset, percentage = percentage, updatedAt = now)
        }
        readingProgressRepo.saveProgress(progress)

        bookRepo.getBookByUuid(bookUuid)?.let { book ->
            bookRepo.updateBook(book.copy(lastReadAt = now, updatedAt = now))
        }
    }

    suspend fun loadProgress(bookUuid: String): ReadingProgress? = readingProgressRepo.getProgress(bookUuid)
}
