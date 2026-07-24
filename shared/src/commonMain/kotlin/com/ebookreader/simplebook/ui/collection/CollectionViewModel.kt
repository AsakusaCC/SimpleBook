package com.ebookreader.simplebook.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.BookmarkService
import com.ebookreader.simplebook.domain.service.NoteService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GroupedItems<T>(
    val book: Book,
    val items: List<T>
)

class CollectionViewModel(
    private val bookmarkService: BookmarkService,
    private val noteService: NoteService,
    private val bookService: BookService
) : ViewModel() {

    val bookmarkGroups: StateFlow<List<GroupedItems<Bookmark>>> =
        combine(
            bookmarkService.getAllBookmarks(),
            bookService.getAllBooks()
        ) { bookmarks, books ->
            val bookMap = books.associateBy { it.uuid }
            bookmarks
                .groupBy { it.bookUuid }
                .mapNotNull { (bookUuid, bms) ->
                    bookMap[bookUuid]?.let { book -> GroupedItems(book, bms) }
                }
                .sortedByDescending { it.items.maxOfOrNull { it.createdAt } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noteGroups: StateFlow<List<GroupedItems<Note>>> =
        combine(
            noteService.getAllNotes(),
            bookService.getAllBooks()
        ) { notes, books ->
            val bookMap = books.associateBy { it.uuid }
            notes
                .groupBy { it.bookUuid }
                .mapNotNull { (bookUuid, ns) ->
                    bookMap[bookUuid]?.let { book -> GroupedItems(book, ns) }
                }
                .sortedByDescending { it.items.maxOfOrNull { it.createdAt } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkService.softDeleteBookmark(bookmark) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { noteService.softDeleteNote(note) }
    }
}
