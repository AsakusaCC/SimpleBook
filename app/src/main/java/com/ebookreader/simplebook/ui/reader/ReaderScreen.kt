package com.ebookreader.simplebook.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.domain.model.Chapter
import com.ebookreader.simplebook.domain.model.ChapterType
import com.ebookreader.simplebook.domain.model.TocEntry

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

    var isTocVisible by remember { mutableStateOf(false) }
    val tocSheetState = rememberModalBottomSheetState()
    var showNoteDialog by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    val isExpanded = windowWidthSizeClass == WindowWidthSizeClass.Expanded

    if (showNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNoteDialog = false; noteText = "" },
            title = { Text("Add Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Note content") },
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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNoteDialog = false; noteText = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Side panel for Expanded screens
        if (isExpanded) {
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

        // Reader content area - extracted to separate composable to avoid
        // RowScope.AnimatedVisibility shadowing the generic AnimatedVisibility
        ReaderPane(
            isLoading = isLoading,
            chapters = chapters,
            currentChapterIndex = currentChapterIndex,
            isToolbarVisible = isToolbarVisible,
            scrollPercentage = scrollPercentage,
            bookTitle = book?.title ?: "",
            isBookmarked = isBookmarked,
            isExpanded = isExpanded,
            isTocVisible = isTocVisible,
            tocSheetState = tocSheetState,
            tocEntries = tocEntries,
            navController = navController,
            onToggleToolbar = viewModel::toggleToolbar,
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
    isExpanded: Boolean,
    isTocVisible: Boolean,
    tocSheetState: SheetState,
    tocEntries: List<TocEntry>,
    navController: NavController?,
    onToggleToolbar: () -> Unit,
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

            when (currentChapter?.type) {
                ChapterType.EPUB_HTML -> {
                    EpubReaderView(
                        htmlContent = currentChapter.content,
                        onScrollPercentageChanged = onScrollPercentageChanged,
                        onChapterFinished = onNextChapter,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                ChapterType.TXT_PLAIN -> {
                    TxtReaderView(
                        paragraphs = currentChapter.content.split("\n"),
                        onScrollPositionChanged = { /* position tracking */ },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                null -> {
                    Text("No content", modifier = Modifier.align(Alignment.Center))
                }
            }

            // Toolbar overlay - top
            AnimatedVisibility(
                visible = isToolbarVisible,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Column {
                    TopAppBar(
                        title = { Text(bookTitle) },
                        navigationIcon = {
                            IconButton(onClick = { navController?.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = onToggleBookmark) {
                                Icon(
                                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Bookmark"
                                )
                            }
                            IconButton(onClick = onShowNoteDialog) {
                                Icon(Icons.Default.Edit, contentDescription = "Add Note")
                            }
                            if (!isExpanded) {
                                IconButton(onClick = onShowToc) {
                                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents")
                                }
                            }
                        },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            }

            // Toolbar overlay - bottom
            AnimatedVisibility(
                visible = isToolbarVisible,
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                        text = "Chapter ${currentChapterIndex + 1} of ${chapters.size}",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Click handler to toggle toolbar (center tap)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleToolbar() }
            )
        }

        // TOC Bottom Sheet (only for Compact/Medium)
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
