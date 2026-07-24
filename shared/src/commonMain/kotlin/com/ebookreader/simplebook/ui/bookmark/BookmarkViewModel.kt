package com.ebookreader.simplebook.ui.bookmark

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.service.BookmarkService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class BookmarkViewModel(
    savedStateHandle: SavedStateHandle,
    private val bookmarkService: BookmarkService
) : ViewModel() {
    private val bookUuid: String = savedStateHandle["bookUuid"] ?: ""

    val bookmarks: StateFlow<List<Bookmark>> =
        (if (bookUuid.isNotEmpty()) bookmarkService.getBookmarksForBook(bookUuid) else bookmarkService.getAllBookmarks())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
