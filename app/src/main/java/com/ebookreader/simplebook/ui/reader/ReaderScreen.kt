package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.domain.model.Chapter
import com.ebookreader.simplebook.domain.model.ChapterType
import com.ebookreader.simplebook.domain.model.AppStrings
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.TocEntry
import com.ebookreader.simplebook.domain.model.getStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    navController: NavController? = null,
    windowWidthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val book by viewModel.book.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val currentChapterIndex by viewModel.currentChapterIndex.collectAsState()
    val isToolbarVisible by viewModel.isToolbarVisible.collectAsState()
    val scrollPercentage by viewModel.scrollPercentage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val tocEntries by viewModel.tocEntries.collectAsState()
    val isBookmarked by viewModel.isBookmarked.collectAsState()
    val bookmarks by viewModel.bookmarks.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }

    var isTocVisible by remember { mutableStateOf(false) }
    val tocSheetState = rememberModalBottomSheetState()
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }
    var isSidePanelVisible by remember { mutableStateOf(false) }

    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false; noteText = "" },
            title = { Text(strings.addNote) },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(strings.noteContent) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.addNote(noteText)
                        }
                        showNoteDialog = false
                        noteText = ""
                    }
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false; noteText = "" }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        if (isExpanded && isSidePanelVisible) {
            ReaderSidePanel(
                tocEntries = tocEntries,
                bookmarks = bookmarks,
                notes = notes,
                currentChapterIndex = currentChapterIndex,
                onChapterSelect = { viewModel.goToChapter(it) },
                onBookmarkClick = { viewModel.goToBookmark(it) },
                onNoteClick = { viewModel.goToNote(it) },
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )
        }

        ReaderPane(
            isLoading = isLoading,
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            isToolbarVisible = isToolbarVisible,
            scrollPercentage = scrollPercentage,
            bookTitle = book?.title ?: "",
            isBookmarked = isBookmarked,
            isTocVisible = isTocVisible,
            tocSheetState = tocSheetState,
            tocEntries = tocEntries,
            navController = navController,
            settings = settings,
            strings = strings,
            isExpanded = isExpanded,
            isSidePanelVisible = isSidePanelVisible,
            onToggleToolbar = viewModel::toggleToolbar,
            onToggleSidePanel = { isSidePanelVisible = !isSidePanelVisible },
            onToggleBookmark = viewModel::toggleBookmark,
            onShowNoteDialog = { showNoteDialog = true },
            onShowToc = { isTocVisible = true },
            onDismissToc = { isTocVisible = false },
            onPreviousChapter = viewModel::previousChapter,
            onNextChapter = viewModel::nextChapter,
            onScrollPercentageChanged = viewModel::onScrollPercentageChanged,
            onChapterSelect = { index ->
                viewModel.goToChapter(index)
                isTocVisible = false
            },
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderPane(
    isLoading: Boolean,
    chapters: List<Chapter>,
    currentChapterIndex: Int,
    isToolbarVisible: Boolean,
    scrollPercentage: Float,
    bookTitle: String,
    isBookmarked: Boolean,
    isTocVisible: Boolean,
    tocSheetState: SheetState,
    tocEntries: List<TocEntry>,
    navController: NavController?,
    settings: ReaderSettings,
    strings: AppStrings,
    isExpanded: Boolean,
    isSidePanelVisible: Boolean,
    onToggleToolbar: () -> Unit,
    onToggleSidePanel: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowNoteDialog: () -> Unit,
    onShowToc: () -> Unit,
    onDismissToc: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onScrollPercentageChanged: (Float) -> Unit,
    onChapterSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            val currentChapter = chapters.getOrNull(currentChapterIndex)
            val textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = settings.fontSize.sp,
                lineHeight = settings.fontSize.sp * settings.lineHeight,
                color = Color(settings.textColor)
            )

            when (currentChapter?.type) {
                ChapterType.EPUB_HTML -> {
                    EpubReaderView(
                        htmlContent = currentChapter.content,
                        onScrollPercentageChanged = onScrollPercentageChanged,
                        onChapterFinished = onNextChapter,
                        backgroundColor = settings.backgroundColor,
                        textColor = settings.textColor,
                        fontSize = settings.fontSize,
                        lineHeight = settings.lineHeight,
                        hasNextChapter = currentChapterIndex < chapters.size - 1,
                        nextChapterText = strings.nextChapter,
                        allReadText = strings.allChaptersRead,
                        onTap = onToggleToolbar,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(settings.backgroundColor))
                    )
                }
                ChapterType.TXT_PLAIN -> {
                    TxtReaderView(
                        paragraphs = currentChapter.content.split("\n"),
                        textStyle = textStyle,
                        onScrollPositionChanged = { /* position tracking */ },
                        onTap = onToggleToolbar,
                        hasNextChapter = currentChapterIndex < chapters.size - 1,
                        onNextChapter = onNextChapter,
                        nextChapterText = strings.nextChapter,
                        endOfChapterTitle = strings.endOfChapter,
                        continueQuestionText = strings.continueQuestion,
                        continueBtnText = strings.continueBtn,
                        stayBtnText = strings.stayBtn,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(settings.backgroundColor))
                    )
                }
                null -> {
                    Text(strings.noContent, modifier = Modifier.align(Alignment.Center))
                }
            }

            if (isToolbarVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                ) {
                    TopAppBar(
                        title = { Text(bookTitle) },
                        navigationIcon = {
                            IconButton(onClick = { navController?.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            if (isExpanded) {
                                IconButton(onClick = onToggleSidePanel) {
                                    Icon(
                                        if (isSidePanelVisible) Icons.AutoMirrored.Filled.List else Icons.AutoMirrored.Filled.MenuBook,
                                        contentDescription = "TOC"
                                    )
                                }
                            }
                            IconButton(onClick = onToggleBookmark) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark"
                                )
                            }
                            IconButton(onClick = onShowNoteDialog) {
                                Icon(Icons.Default.Edit, contentDescription = strings.addNote)
                            }
                            if (!isExpanded) {
                                IconButton(onClick = onShowToc) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "TOC")
                                }
                            }
                        }
                    )
                }
            }

            if (isToolbarVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = currentChapter?.title ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onPreviousChapter,
                            enabled = currentChapterIndex > 0
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, "Previous")
                        }
                        LinearProgressIndicator(
                            progress = { scrollPercentage },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                        )
                        IconButton(
                            onClick = onNextChapter,
                            enabled = currentChapterIndex < chapters.size - 1
                        ) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook, "Next")
                        }
                    }
                    Text(
                        text = strings.chapterOf(currentChapterIndex + 1, chapters.size),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        if (!isExpanded && isTocVisible) {
            ModalBottomSheet(
                onDismissRequest = onDismissToc,
                sheetState = tocSheetState
            ) {
                TocPanel(
                    entries = tocEntries,
                    currentChapterIndex = currentChapterIndex,
                    onChapterSelect = onChapterSelect,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
