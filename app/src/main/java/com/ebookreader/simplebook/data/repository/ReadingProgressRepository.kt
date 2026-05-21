package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity
import com.ebookreader.simplebook.domain.model.ReadingProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) {
    suspend fun getProgress(bookId: Long): ReadingProgress? =
        readingProgressDao.getProgress(bookId)?.toDomain()

    suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    private fun ReadingProgressEntity.toDomain() = ReadingProgress(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        percentage = percentage,
        updatedAt = updatedAt
    )

    private fun ReadingProgress.toEntity() = ReadingProgressEntity(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        percentage = percentage,
        updatedAt = updatedAt
    )
}
