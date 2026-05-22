package com.ebookreader.simplebook.ui.bookmark

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.service.BookmarkService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class BookmarkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookmarkService: BookmarkService
) : ViewModel() {
    private val bookId: Long = savedStateHandle["bookId"] ?: 0L

    val bookmarks: StateFlow<List<Bookmark>> =
        (if (bookId > 0) bookmarkService.getBookmarksForBook(bookId) else bookmarkService.getAllBookmarks())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
