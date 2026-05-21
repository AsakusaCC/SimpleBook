package com.ebookreader.simplebook.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Chapter
import com.ebookreader.simplebook.domain.model.ChapterType
import com.ebookreader.simplebook.domain.model.TocEntry
import com.ebookreader.simplebook.domain.service.BookService
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

    // Keep epubBook reference for lazy chapter loading
    private var epubBookRef: nl.siegmann.epublib.domain.Book? = null
    private var saveJob: Job? = null

    init {
        loadBook()
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
        }
    }

    fun nextChapter() = goToChapter(_currentChapterIndex.value + 1)
    fun previousChapter() = goToChapter(_currentChapterIndex.value - 1)

    fun onScrollPercentageChanged(percentage: Float) {
        _scrollPercentage.value = percentage
        debounceSaveProgress()
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
