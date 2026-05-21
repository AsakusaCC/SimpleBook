package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.domain.model.Highlight
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightService @Inject constructor(
    private val highlightRepo: HighlightRepository
) {
    fun getHighlightsForChapter(bookId: Long, chapterIndex: Int): Flow<List<Highlight>> =
        highlightRepo.getHighlightsForChapter(bookId, chapterIndex)

    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>> =
        highlightRepo.getHighlightsForBook(bookId)

    suspend fun addHighlight(highlight: Highlight): Long =
        highlightRepo.addHighlight(highlight)

    suspend fun deleteHighlight(highlight: Highlight) =
        highlightRepo.deleteHighlight(highlight)
}
