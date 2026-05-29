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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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

    private val _shelfData = combine(
        _shelfBooks,
        _foldersWithCount,
        _folderBooks,
        _bookProgress
    ) { shelfBooks, foldersWithCount, folderBooks, progressMap ->
        ShelfData(shelfBooks, foldersWithCount, folderBooks, progressMap)
    }

    private val _isDataReady = MutableStateFlow(false)
    val isDataReady: StateFlow<Boolean> = _isDataReady.asStateFlow()

    val currentItems: StateFlow<List<ShelfItem>> = combine(
        _currentFolderId,
        _shelfData,
        sortOrderFlow
    ) { folderId, data, sortOrder ->
        val sorted: (List<ShelfItem.BookItem>) -> List<ShelfItem.BookItem> = { items ->
            when (sortOrder) {
                SortOrder.LAST_READ -> items.sortedByDescending { it.book.lastReadAt ?: 0L }
                SortOrder.NAME -> items.sortedBy { it.book.title.lowercase() }
            }
        }

        if (folderId != null) {
            sorted(data.folderBooks.map { book ->
                ShelfItem.BookItem(book, data.progressMap[book.uuid] ?: 0.0)
            })
        } else {
            val folderItems = data.foldersWithCount.map { (folder, count) ->
                ShelfItem.FolderItem(folder, count)
            }
            val bookItems = sorted(data.shelfBooks.map { book ->
                ShelfItem.BookItem(book, data.progressMap[book.uuid] ?: 0.0)
            })
            folderItems + bookItems
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allFoldersForDialog: StateFlow<List<Pair<Folder, Int>>> = _foldersWithCount

    init {
        viewModelScope.launch {
            bookService.getShelfBooks().collect {
                _shelfBooks.value = it
                if (!_isDataReady.value) _isDataReady.value = true
            }
        }
        viewModelScope.launch {
            // Refresh folder counts whenever folders OR books change
            combine(folderService.getAllFolders(), bookService.getAllBooks()) { folders, _ ->
                folders.map { folder ->
                    folder to folderService.getBookCountInFolder(folder.uuid)
                }
            }.collect { _foldersWithCount.value = it }
        }
        viewModelScope.launch {
            _currentFolderId
                .flatMapLatest { folderId ->
                    if (folderId != null) bookService.getBooksInFolder(folderId)
                    else flowOf(emptyList())
                }
                .collect { _folderBooks.value = it }
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

    fun deleteFolder(folderUuid: String) {
        viewModelScope.launch {
            // Move all books in this folder back to shelf
            val books = bookService.getAllBooksNow().filter { it.folderId == folderUuid }
            for (book in books) {
                bookService.moveBookToFolder(book.uuid, null)
            }
            folderService.softDeleteFolder(folderUuid)
        }
    }
}

private data class ShelfData(
    val shelfBooks: List<Book>,
    val foldersWithCount: List<Pair<Folder, Int>>,
    val folderBooks: List<Book>,
    val progressMap: Map<String, Double>
)
