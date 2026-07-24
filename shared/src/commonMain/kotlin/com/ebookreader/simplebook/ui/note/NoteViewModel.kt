package com.ebookreader.simplebook.ui.note

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.service.NoteService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope

class NoteViewModel(
    savedStateHandle: SavedStateHandle,
    private val noteService: NoteService
) : ViewModel() {
    private val bookUuid: String = savedStateHandle["bookUuid"] ?: ""

    val notes: StateFlow<List<Note>> =
        (if (bookUuid.isNotEmpty()) noteService.getNotesForBook(bookUuid) else noteService.getAllNotes())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
