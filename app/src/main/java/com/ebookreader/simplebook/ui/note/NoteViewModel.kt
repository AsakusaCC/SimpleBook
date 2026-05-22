package com.ebookreader.simplebook.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.service.NoteService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class NoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val noteService: NoteService
) : ViewModel() {
    private val bookId: Long = savedStateHandle["bookId"] ?: 0L

    val notes: StateFlow<List<Note>> =
        (if (bookId > 0) noteService.getNotesForBook(bookId) else noteService.getAllNotes())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
