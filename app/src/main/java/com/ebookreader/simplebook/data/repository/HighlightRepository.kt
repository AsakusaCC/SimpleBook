package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import com.ebookreader.simplebook.domain.model.Highlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao
) {
    fun getHighlightsForChapter(bookUuid: String, chapterIndex: Int): Flow<List<Highlight>> =
        highlightDao.getHighlightsForChapter(bookUuid, chapterIndex).map { list -> list.map { it.toDomain() } }

    fun getHighlightsForBook(bookUuid: String): Flow<List<Highlight>> =
        highlightDao.getHighlightsForBook(bookUuid).map { list -> list.map { it.toDomain() } }

    suspend fun getHighlightsForBookNow(bookUuid: String): List<Highlight> =
        highlightDao.getHighlightsForBookNow(bookUuid).map { it.toDomain() }

    suspend fun getAllHighlightsForBookNow(bookUuid: String): List<Highlight> =
        highlightDao.getAllHighlightsForBookNow(bookUuid).map { it.toDomain() }

    suspend fun addHighlight(highlight: Highlight) { highlightDao.insert(highlight.toEntity()) }

    suspend fun softDeleteHighlight(uuid: String) { highlightDao.softDelete(uuid) }

    suspend fun hardDeleteByBook(bookUuid: String) { highlightDao.hardDeleteByBook(bookUuid) }

    private fun HighlightEntity.toDomain() = Highlight(
        uuid = uuid, bookUuid = bookUuid, chapterIndex = chapterIndex,
        startOffset = startOffset, endOffset = endOffset, color = color.toLong(),
        note = note, createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )

    private fun Highlight.toEntity() = HighlightEntity(
        uuid = uuid, bookUuid = bookUuid, chapterIndex = chapterIndex,
        startOffset = startOffset, endOffset = endOffset, color = color.toInt(),
        note = note, createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )
}
