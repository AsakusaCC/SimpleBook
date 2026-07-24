package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity
import com.ebookreader.simplebook.domain.model.ReadingProgress

class ReadingProgressRepository constructor(
    private val readingProgressDao: ReadingProgressDao
) {
    suspend fun getProgress(bookUuid: String): ReadingProgress? =
        readingProgressDao.getProgress(bookUuid)?.toDomain()

    suspend fun getProgressIncludingDeleted(bookUuid: String): ReadingProgress? =
        readingProgressDao.getProgressIncludingDeleted(bookUuid)?.toDomain()

    suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    suspend fun hardDeleteByBook(bookUuid: String) {
        readingProgressDao.hardDeleteByBook(bookUuid)
    }

    private fun ReadingProgressEntity.toDomain() = ReadingProgress(
        uuid = uuid, bookUuid = bookUuid, chapterIndex = chapterIndex,
        charOffset = charOffset, percentage = percentage, updatedAt = updatedAt, isDeleted = isDeleted
    )

    private fun ReadingProgress.toEntity() = ReadingProgressEntity(
        uuid = uuid, bookUuid = bookUuid, chapterIndex = chapterIndex,
        charOffset = charOffset, percentage = percentage, updatedAt = updatedAt, isDeleted = isDeleted
    )
}
