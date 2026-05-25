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
    fun getHighlightsForChapter(bookId: Long, chapterIndex: Int): Flow<List<Highlight>> =
        highlightDao.getHighlightsForChapter(bookId, chapterIndex).map { list -> list.map { it.toDomain() } }

    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>> =
        highlightDao.getHighlightsForBook(bookId).map { list -> list.map { it.toDomain() } }

    suspend fun addHighlight(highlight: Highlight): Long =
        highlightDao.insert(highlight.toEntity())

    suspend fun deleteHighlight(highlight: Highlight) =
        highlightDao.delete(highlight.toEntity())

    private fun HighlightEntity.toDomain() = Highlight(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        color = color.toLong(),
        note = note,
        createdAt = createdAt,
        syncVersion = syncVersion,
        lastSyncedAt = lastSyncedAt
    )

    private fun Highlight.toEntity() = HighlightEntity(
        id = id,
        bookId = bookId,
        chapterIndex = chapterIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        color = color.toInt(),
        note = note,
        createdAt = createdAt,
        syncVersion = syncVersion,
        lastSyncedAt = lastSyncedAt
    )
}
