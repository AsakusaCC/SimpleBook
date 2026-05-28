package com.ebookreader.simplebook.ui.booklist

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.Folder
import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ShelfItem
import com.ebookreader.simplebook.domain.model.SortOrder
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.FileImportService
import com.ebookreader.simplebook.domain.service.FolderService
import com.ebookreader.simplebook.domain.service.ReadingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookService: BookService,
    private val folderService: FolderService,
    private val fileImportService: FileImportService,
    private val settingsDataStore: SettingsDataStore,
    private val readingService: ReadingService
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName: StateFlow<String?> = _currentFolderName.asStateFlow()

    private val _isSpeedDialExpanded = MutableStateFlow(false)
    val isSpeedDialExpanded: StateFlow<Boolean> = _isSpeedDialExpanded.asStateFlow()

    private val _bookProgress = MutableStateFlow<Map<String, Double>>(emptyMap())
    val bookProgress: StateFlow<Map<String, Double>> = _bookProgress.asStateFlow()

    private val _shelfBooks = MutableStateFlow<List<Book>>(emptyList())
    private val _foldersWithCount = MutableStateFlow<List<Pair<Folder, Int>>>(emptyList())
    private val _folderBooks = MutableStateFlow<List<Book>>(emptyList())

    private val sortOrderFlow = settingsDataStore.settings
        .map { it.sortOrder }
        .distinctUntilChanged()

    val currentItems: StateFlow<List<ShelfItem>> = combine(
        _currentFolderId,
        _shelfBooks,
        _foldersWithCount,
        _folderBooks,
        _bookProgress,
        sortOrderFlow
    ) { folderId, shelfBooks, foldersWithCount, folderBooks, progressMap, sortOrder ->
        val sorted: (List<ShelfItem.BookItem>) -> List<ShelfItem.BookItem> = { items ->
            when (sortOrder) {
                SortOrder.LAST_READ -> items.sortedByDescending { it.book.lastReadAt ?: 0L }
                SortOrder.NAME -> items.sortedBy { it.book.title.lowercase() }
            }
        }

        if (folderId != null) {
            sorted(folderBooks.map { book ->
                ShelfItem.BookItem(book, progressMap[book.uuid] ?: 0.0)
            })
        } else {
            val folderItems = foldersWithCount.map { (folder, count) ->
                ShelfItem.FolderItem(folder, count)
            }
            val bookItems = sorted(shelfBooks.map { book ->
                ShelfItem.BookItem(book, progressMap[book.uuid] ?: 0.0)
            })
            folderItems + bookItems
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFoldersForDialog: StateFlow<List<Pair<Folder, Int>>> = _foldersWithCount

    init {
        viewModelScope.launch {
            bookService.getShelfBooks().collect { _shelfBooks.value = it }
        }
        viewModelScope.launch {
            folderService.getAllFolders().collect { folders ->
                _foldersWithCount.value = folders.map { folder ->
                    folder to folderService.getBookCountInFolder(folder.uuid)
                }
            }
        }
        viewModelScope.launch {
            _currentFolderId.collect { folderId ->
                if (folderId != null) {
                    bookService.getBooksInFolder(folderId).collect { books ->
                        _folderBooks.value = books
                    }
                } else {
                    _folderBooks.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            bookService.getAllBooks().collect { bookList ->
                val progressMap = mutableMapOf<String, Double>()
                for (book in bookList) {
                    val progress = readingService.loadProgress(book.uuid)
                    if (progress != null && progress.percentage > 0.0) {
                        progressMap[book.uuid] = progress.percentage
                    }
                }
                _bookProgress.value = progressMap
            }
        }
    }

    fun toggleSpeedDial() {
        _isSpeedDialExpanded.value = !_isSpeedDialExpanded.value
    }

    fun dismissSpeedDial() {
        _isSpeedDialExpanded.value = false
    }

    fun createImportIntent(): Intent = fileImportService.createImportIntent()

    fun importFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            fileImportService.importFromUris(uris)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookService.softDeleteBook(book.uuid)
        }
    }

    fun updateLayoutMode(layoutMode: LayoutMode) {
        viewModelScope.launch {
            settingsDataStore.updateLayoutMode(layoutMode)
        }
    }

    fun updateSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            settingsDataStore.updateSortOrder(sortOrder)
        }
    }

    fun enterFolder(folderId: String, folderName: String) {
        _currentFolderId.value = folderId
        _currentFolderName.value = folderName
    }

    fun exitFolder() {
        _currentFolderId.value = null
        _currentFolderName.value = null
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            folderService.createFolder(name)
        }
    }

    fun moveBookToFolder(bookUuid: String, folderId: String?) {
        viewModelScope.launch {
            bookService.moveBookToFolder(bookUuid, folderId)
        }
    }
}
