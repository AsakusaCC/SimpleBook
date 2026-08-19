package com.ebookreader.simplebook.ui.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.PdfPageCache
import com.ebookreader.simplebook.data.parser.PdfPageLoader
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.parser.coercePdfPage
import com.ebookreader.simplebook.data.parser.openPdf
import com.ebookreader.simplebook.data.parser.pdfOverallPercentage
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Chapter
import com.ebookreader.simplebook.domain.model.ChapterType
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.TocEntry
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.BookmarkService
import com.ebookreader.simplebook.domain.service.NoteService
import com.ebookreader.simplebook.domain.service.ReadingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

class ReaderViewModel(
    savedStateHandle: SavedStateHandle,
    private val bookService: BookService,
    private val readingService: ReadingService,
    private val bookmarkService: BookmarkService,
    private val noteService: NoteService,
    private val epubParser: EpubParser,
    private val txtParser: TxtParser,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    private val bookUuid: String = savedStateHandle["bookUuid"] ?: ""
    private val initialChapterIndex: Int = savedStateHandle["chapterIndex"] ?: -1
    private val initialCharOffset: Long = savedStateHandle["charOffset"] ?: 0L

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

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

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    fun clearLoadError() {
        _loadError.value = null
    }

    private val _isBookmarked = MutableStateFlow(false)
    val isBookmarked: StateFlow<Boolean> = _isBookmarked.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _pdfState = MutableStateFlow<PdfReaderState?>(null)
    val pdfState: StateFlow<PdfReaderState?> = _pdfState.asStateFlow()

    private var epubBookRef: nl.siegmann.epublib.domain.Book? = null
    private var saveJob: Job? = null

    init {
        loadBook()
        collectBookmarks()
        collectNotes()
    }

    private fun collectBookmarks() {
        viewModelScope.launch {
            bookmarkService.getBookmarksForBook(bookUuid).collect { list ->
                _bookmarks.value = list
            }
        }
    }

    private fun collectNotes() {
        viewModelScope.launch {
            noteService.getNotesForBook(bookUuid).collect { list ->
                _notes.value = list
            }
        }
    }

    private fun loadBook() {
        viewModelScope.launch {
            val book = bookService.getBookByUuid(bookUuid) ?: run {
                _isLoading.value = false
                return@launch
            }
            _book.value = book

            when (book.format) {
                BookFormat.EPUB -> loadEpubChapters(book)
                BookFormat.TXT -> loadTxtChapters(book)
                BookFormat.PDF -> loadPdf(book)
            }

            readingService.loadProgress(bookUuid)?.let { progress ->
                val isPdf = book.format == BookFormat.PDF
                val pdfTotal = _pdfState.value?.pageCount ?: 0
                _currentChapterIndex.value = if (isPdf) {
                    coercePdfPage(progress.chapterIndex, pdfTotal)
                } else {
                    progress.chapterIndex
                }
                _scrollPercentage.value = when {
                    isPdf -> pdfOverallPercentage(_currentChapterIndex.value, pdfTotal).toFloat()
                    progress.charOffset > 0 -> (progress.charOffset / 10000.0).toFloat().coerceIn(0f, 1f)
                    else -> {
                        val totalChapters = _chapters.value.size
                        if (totalChapters > 0) {
                            ((progress.percentage * totalChapters) - progress.chapterIndex).toFloat().coerceIn(0f, 1f)
                        } else 0f
                    }
                }
            }

            // Override with position from Collection navigation
            if (initialChapterIndex >= 0) {
                _currentChapterIndex.value = initialChapterIndex
                _scrollPercentage.value = 0f
            }

            _isLoading.value = false
            refreshBookmarkStatus()
        }
    }

    private fun loadEpubChapters(book: Book) {
        val result = epubParser.parse(File(book.filePath))
        if (result == null) {
            println("ReaderViewModel: failed to parse EPUB (corrupt?): ${book.filePath}")
            _loadError.value = "无法打开此书：文件可能已损坏"
            return
        }
        epubBookRef = result.epubBook
        println("ReaderViewModel: EPUB parsed: title=${result.title}, spineCount=${result.chapterCount}")

        // Collect all spine indices covered by the TOC (including children)
        val tocCoveredIndices = mutableSetOf<Int>()
        fun collectTocIndices(entries: List<TocEntry>) {
            for (entry in entries) {
                if (entry.chapterIndex >= 0) tocCoveredIndices.add(entry.chapterIndex)
                collectTocIndices(entry.children)
            }
        }
        collectTocIndices(result.tableOfContents.entries)

        val chapters = mutableListOf<Chapter>()
        // Track cover/missing entries to prepend to TOC
        val missingTocEntries = mutableListOf<TocEntry>()
        epubBookRef?.let { epubBook ->
            for (i in 0 until result.chapterCount) {
                val spineRef = epubBook.spine.spineReferences[i]
                val rawContent = String(spineRef.resource.data, Charsets.UTF_8)
                val href = spineRef.resource.href ?: ""
                val content = resolveImageReferences(rawContent, epubBook, href)

                val chapterIndex = chapters.size
                val tocTitle = result.tableOfContents.entries.findTocTitleForChapter(i)
                val htmlTitle = extractHtmlTitle(rawContent)
                val title = tocTitle ?: htmlTitle ?: "Chapter ${chapterIndex + 1}"
                println("ReaderViewModel: Chapter $chapterIndex (spine $i): title=$title, contentLength=${content.length}")
                chapters.add(Chapter(
                    index = chapterIndex,
                    title = title,
                    content = content,
                    type = ChapterType.EPUB_HTML
                ))
                // Track chapters not in original TOC (e.g. cover page)
                if (i !in tocCoveredIndices) {
                    missingTocEntries.add(TocEntry(title = title, chapterIndex = chapterIndex))
                }
            }
        }
        // Keep original TOC hierarchy intact, prepend missing entries (like cover)
        _tocEntries.value = missingTocEntries + result.tableOfContents.entries
        println("ReaderViewModel: Total chapters: ${chapters.size}, TOC entries: ${_tocEntries.value.size}")
        _chapters.value = chapters
    }

    private fun extractHtmlTitle(html: String): String? {
        val regex = Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
        return regex.find(html)?.groupValues?.get(1)?.trim()?.ifBlank { null }
    }

    /** Resolve image references in EPUB HTML to base64 data URIs so WebView can render them. */
    private fun resolveImageReferences(
        html: String,
        epubBook: nl.siegmann.epublib.domain.Book,
        resourceHref: String
    ): String {
        val basePath = resourceHref.substringBeforeLast('/', "")
        var result = html

        // Resolve <img src="...">
        val imgRegex = Regex("""(<img\s[^>]*?src\s*=\s*["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
        result = imgRegex.replace(result) { match ->
            val dataUri = resolveToDataUri(match.groupValues[2], basePath, epubBook)
            if (dataUri != null) "${match.groupValues[1]}$dataUri${match.groupValues[3]}" else match.value
        }

        // Resolve <image xlink:href="..."> in SVG
        val svgImageRegex = Regex("""(<image\s[^>]*?(?:xlink:)?href\s*=\s*["'])([^"']+)(["'])""", RegexOption.IGNORE_CASE)
        result = svgImageRegex.replace(result) { match ->
            val dataUri = resolveToDataUri(match.groupValues[2], basePath, epubBook)
            if (dataUri != null) "${match.groupValues[1]}$dataUri${match.groupValues[3]}" else match.value
        }

        return result
    }

    private fun resolveToDataUri(
        src: String,
        basePath: String,
        epubBook: nl.siegmann.epublib.domain.Book
    ): String? {
        if (src.startsWith("data:") || src.startsWith("http:") || src.startsWith("https:") || src.startsWith("file:")) return null

        val resolvedPath = normalizePath(if (basePath.isNotEmpty()) "$basePath/$src" else src)

        val resource = try {
            epubBook.resources.getByHref(resolvedPath)
                ?: epubBook.resources.getByHref(URLDecoder.decode(resolvedPath, "UTF-8"))
        } catch (_: Exception) { null } ?: return null

        if (resource.data == null || resource.data.isEmpty()) return null

        val mimeType = resource.mediaType?.name ?: guessMimeType(src)
        // TODO: Desktop compatibility - use java.util.Base64 instead of android.util.Base64
        val base64 = java.util.Base64.getEncoder().encodeToString(resource.data)
        return "data:$mimeType;base64,$base64"
    }

    private fun normalizePath(path: String): String {
        val parts = path.split("/").toMutableList()
        val result = mutableListOf<String>()
        for (part in parts) {
            when {
                part == ".." -> if (result.isNotEmpty()) result.removeAt(result.lastIndex)
                part != "." && part.isNotEmpty() -> result.add(part)
            }
        }
        return result.joinToString("/")
    }

    private fun guessMimeType(path: String): String = when {
        path.endsWith(".png", ignoreCase = true) -> "image/png"
        path.endsWith(".gif", ignoreCase = true) -> "image/gif"
        path.endsWith(".svg", ignoreCase = true) -> "image/svg+xml"
        path.endsWith(".webp", ignoreCase = true) -> "image/webp"
        else -> "image/jpeg"
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

    private suspend fun loadPdf(book: Book) {
        val doc = withContext(Dispatchers.IO) { openPdf(File(book.filePath)) }
        if (doc == null || doc.pageCount <= 0) {
            runCatching { doc?.close() }
            println("ReaderViewModel: failed to parse PDF (corrupt/encrypted?): ${book.filePath}")
            _loadError.value = "无法打开此书：文件可能已加密或损坏"
            return
        }
        val loader = PdfPageLoader(doc, PdfPageCache(), bookUuid)
        _pdfState.value = PdfReaderState(pageCount = doc.pageCount, loader = loader)
        _tocEntries.value = (0 until doc.pageCount).map {
            TocEntry(title = "第 ${it + 1} 页", chapterIndex = it)
        }
        // 后台预扫每页宽高比（未渲染仅读尺寸），供占位比例使用
        viewModelScope.launch(Dispatchers.IO) {
            val ratios = (0 until doc.pageCount).map { loader.aspectRatio(it) }
            _pdfState.value = _pdfState.value?.copy(aspectRatios = ratios)
        }
    }

    fun toggleToolbar() {
        _isToolbarVisible.value = !_isToolbarVisible.value
    }

    fun goToChapter(index: Int) {
        val inBounds = if (_book.value?.format == BookFormat.PDF) {
            index in 0 until (_pdfState.value?.pageCount ?: 0)
        } else {
            index in _chapters.value.indices
        }
        if (inBounds) {
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

    fun onPageChanged(page: Int) {
        _currentChapterIndex.value = page
        _scrollPercentage.value = pdfOverallPercentage(page, _pdfState.value?.pageCount ?: 0).toFloat()
        debounceSaveProgress()
        refreshBookmarkStatus()
    }

    private fun refreshBookmarkStatus() {
        viewModelScope.launch {
            _isBookmarked.value = bookmarkService.isBookmarked(bookUuid, _currentChapterIndex.value)
        }
    }

    fun toggleBookmark() {
        viewModelScope.launch {
            if (_isBookmarked.value) {
                bookmarkService.deleteBookmarkForPosition(bookUuid, _currentChapterIndex.value)
                _isBookmarked.value = false
            } else {
                val chapterTitle = if (_book.value?.format == BookFormat.PDF) {
                    "第 ${_currentChapterIndex.value + 1} 页"
                } else {
                    _chapters.value.getOrNull(_currentChapterIndex.value)?.title ?: ""
                }
                bookmarkService.addBookmark(bookUuid, _currentChapterIndex.value, 0, chapterTitle)
                _isBookmarked.value = true
            }
        }
    }

    fun addNote(content: String) {
        viewModelScope.launch {
            noteService.addNote(
                Note(
                    bookUuid = bookUuid,
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
            delay(1000)
            saveCurrentProgress()
        }
    }

    private fun saveCurrentProgress() {
        viewModelScope.launch {
            val isPdf = _book.value?.format == BookFormat.PDF
            val pdfTotal = _pdfState.value?.pageCount ?: 0
            val chapterPct = _scrollPercentage.value.toDouble().coerceIn(0.0, 1.0)

            val overallPct = when {
                isPdf -> pdfOverallPercentage(_currentChapterIndex.value, pdfTotal)
                else -> {
                    val totalChapters = _chapters.value.size
                    if (totalChapters > 0) {
                        ((_currentChapterIndex.value + chapterPct) / totalChapters).coerceIn(0.0, 1.0)
                    } else 0.0
                }
            }

            readingService.saveProgress(
                bookUuid = bookUuid,
                chapterIndex = _currentChapterIndex.value,
                charOffset = if (isPdf) 0L else (chapterPct * 10000).toLong(),
                percentage = overallPct
            )
        }
    }

    override fun onCleared() {
        saveJob?.cancel()
        _pdfState.value?.loader?.close()
        // Save progress immediately when leaving reader
        runBlocking { saveCurrentProgress() }
        super.onCleared()
    }
}

private fun List<TocEntry>.findTocTitleForChapter(chapterIndex: Int): String? {
    for (entry in this) {
        if (entry.chapterIndex == chapterIndex) return entry.title
        val childResult = entry.children.findTocTitleForChapter(chapterIndex)
        if (childResult != null) return childResult
    }
    return null
}
