package com.ebookreader.simplebook.ui.booklist

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.FileImportService
import com.ebookreader.simplebook.domain.service.ReadingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookService: BookService,
    private val fileImportService: FileImportService,
    private val settingsDataStore: SettingsDataStore,
    private val readingService: ReadingService
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val books: StateFlow<List<Book>> = bookService.getAllBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _bookProgress = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val bookProgress: StateFlow<Map<Long, Double>> = _bookProgress.asStateFlow()

    init {
        viewModelScope.launch {
            books.collect { bookList ->
                val progressMap = mutableMapOf<Long, Double>()
                for (book in bookList) {
                    val progress = readingService.loadProgress(book.id)
                    if (progress != null && progress.percentage > 0.0) {
                        progressMap[book.id] = progress.percentage
                    }
                }
                _bookProgress.value = progressMap
            }
        }
    }

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
