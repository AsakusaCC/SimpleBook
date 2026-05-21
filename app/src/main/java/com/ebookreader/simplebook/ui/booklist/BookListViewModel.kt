package com.ebookreader.simplebook.ui.booklist

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.FileImportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookService: BookService,
    private val fileImportService: FileImportService
) : ViewModel() {

    val books: StateFlow<List<Book>> = bookService.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createImportIntent(): Intent = fileImportService.createImportIntent()

    fun importFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            fileImportService.importFromUris(uris)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookService.deleteBook(book)
        }
    }
}
