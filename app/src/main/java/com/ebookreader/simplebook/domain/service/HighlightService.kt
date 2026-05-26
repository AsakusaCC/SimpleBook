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
    fun getHighlightsForChapter(bookUuid: String, chapterIndex: Int): Flow<List<Highlight>> =
        highlightRepo.getHighlightsForChapter(bookUuid, chapterIndex)

    fun getHighlightsForBook(bookUuid: String): Flow<List<Highlight>> =
        highlightRepo.getHighlightsForBook(bookUuid)

    suspend fun addHighlight(highlight: Highlight) = highlightRepo.addHighlight(highlight)

    suspend fun softDeleteHighlight(highlight: Highlight) = highlightRepo.softDeleteHighlight(highlight.uuid)
}
