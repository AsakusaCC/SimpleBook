package com.ebookreader.simplebook.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Chapter
import com.ebookreader.simplebook.domain.model.ChapterType
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.TocEntry
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.BookmarkService
import com.ebookreader.simplebook.domain.service.NoteService
import com.ebookreader.simplebook.domain.service.ReadingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ReaderViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val bookService: BookService,
    private val readingService: ReadingService,
    private val bookmarkService: BookmarkService,
    private val noteService: NoteService,
    private val epubParser: EpubParser,
    private val txtParser: TxtParser
) : ViewModel() {

    private val bookId: Long = savedStateHandle["bookId"] ?: 0L

    private val _book = MutableStateFlow<Book?>(null)
    val book: StateFlow<Book?> = _book.asStateFlow()

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()

    private val _currentChapterIndex = MutableStateFlow(0)
    val currentChapterIndex: StateFlow<Int> = _currentChapterIndex.asStateFlow()

    private val _isToolbarVisible = MutableStateFlow(false)
    val isToolbarVisible: StateFlow<Boolean> = _isToolbarVisible.asStateFlow()

    private val _scrollPercentage = MutableStateFlow(0f)
    val scrollPercentage: StateFlow<Float> = _scrollPercentage.asStateFlow()

    private val _tocEntries = MutableStateFlow<List<TocEntry>>(emptyList())
    val tocEntries: StateFlow<List<TocEntry>> = _tocEntries.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    // Keep epubBook reference for lazy chapter loading
    private var epubBookRef: nl.siegmann.epublib.domain.Book? = null
    private var saveJob: Job? = null

    init {
        loadBook()
        collectBookmarks()
        collectNotes()
    }

    private fun collectBookmarks() {
        viewModelScope.launch {
            bookmarkService.getBookmarksForBook(bookId).collect { list ->
                _bookmarks.value = list
            }
        }
    }

    private fun collectNotes() {
        viewModelScope.launch {
            noteService.getNotesForBook(bookId).collect { list ->
                _notes.value = list
            }
        }
    }

    private fun loadBook() {
        viewModelScope.launch {
            val book = bookService.getBookById(bookId) ?: run {
                _isLoading.value = false
                return@launch
            }
            _book.value = book

            when (book.format) {
                BookFormat.EPUB -> loadEpubChapters(book)
                BookFormat.TXT -> loadTxtChapters(book)
            }

            // Restore reading progress
            readingService.loadProgress(bookId)?.let { progress ->
                _currentChapterIndex.value = progress.chapterIndex
                _scrollPercentage.value = progress.percentage.toFloat()
            }

            _isLoading.value = false

            // Check bookmark status for current chapter
            refreshBookmarkStatus()
        }
    }

    private fun loadEpubChapters(book: Book) {
        val result = epubParser.parse(File(book.filePath))
        epubBookRef = result.epubBook
        _tocEntries.value = result.tableOfContents.entries
        // Load all chapter content
        val chapters = mutableListOf<Chapter>()
        epubBookRef?.let { epubBook ->
            for (i in 0 until result.chapterCount) {
                val content = epubParser.getChapterContent(epubBook, i) ?: continue
                val title = result.tableOfContents.entries
                    .findTocTitleForChapter(i) ?: "Chapter ${i + 1}"
                chapters.add(Chapter(
                    index = i,
                    title = title,
                    content = content,
                    type = ChapterType.EPUB_HTML
                ))
            }
        }
        _chapters.value = chapters
    }

    private fun loadTxtChapters(book: Book) {
        val result = txtParser.parse(File(book.filePath))
        _chapters.value = result.chapters
        _tocEntries.value = result.chapters.map { chapter ->
            TocEntry(
                title = chapter.title,
                chapterIndex = chapter.index
            )
        }
    }

    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun goToChapter(index: Int) {
        if (index in _chapters.value.indices) {
            saveCurrentProgress()
            _currentChapterIndex.value = index
            _scrollPercentage.value = 0f
            refreshBookmarkStatus()
        }
    }

    fun nextChapter() = goToChapter(_currentChapterIndex.value + 1)
    fun previousChapter() = goToChapter(_currentChapterIndex.value - 1)

    fun onScrollPercentageChanged(percentage: Float) {
        _scrollPercentage.value = percentage
        debounceSaveProgress()
    }

    private fun refreshBookmarkStatus() {
        viewModelScope.launch {
            _isBookmarked.value = bookmarkService.isBookmarked(bookId, _currentChapterIndex.value)
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            if (_isBookmarked.value) {
                bookmarkService.deleteBookmarkForPosition(bookId, _currentChapterIndex.value)
                _isBookmarked.value = false
            } else {
                val chapterTitle = _chapters.value.getOrNull(_currentChapterIndex.value)?.title ?: ""
                bookmarkService.addBookmark(bookId, _currentChapterIndex.value, 0, chapterTitle)
                _isBookmarked.value = true
            }
        }
    }

    fun addNote(content: String) {
        viewModelScope.launch {
            noteService.addNote(
                Note(
                    bookId = bookId,
                    chapterIndex = _currentChapterIndex.value,
                    content = content
                )
            )
        }
    }

    fun goToBookmark(bookmark: Bookmark) {
        goToChapter(bookmark.chapterIndex)
    }

    fun goToNote(note: Note) {
        goToChapter(note.chapterIndex)
    }

    private fun debounceSaveProgress() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(5000)
            saveCurrentProgress()
        }
    }

    private fun saveCurrentProgress() {
        viewModelScope.launch {
            readingService.saveProgress(
                bookId = bookId,
                chapterIndex = _currentChapterIndex.value,
                charOffset = 0,
                percentage = _scrollPercentage.value.toDouble()
            )
        }
    }

    override fun onCleared() {
        saveJob?.cancel()
        // Synchronous save is not possible in coroutine scope,
        // but we already saved on chapter change and debounced
        super.onCleared()
    }
}

// Helper to find TOC title for a spine index
private fun List<TocEntry>.findTocTitleForChapter(chapterIndex: Int): String? {
    for (entry in this) {
        if (entry.chapterIndex == chapterIndex) return entry.title
        val childResult = entry.children.findTocTitleForChapter(chapterIndex)
        if (childResult != null) return childResult
    }
    return null
}
