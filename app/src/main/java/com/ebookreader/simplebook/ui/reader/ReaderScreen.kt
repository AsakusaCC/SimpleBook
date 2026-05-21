package com.ebookreader.simplebook.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.ebookreader.simplebook.domain.model.ChapterType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    navController: NavController? = null,
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

    var isTocVisible by remember { mutableStateOf(false) }
    val tocSheetState = rememberModalBottomSheetState()

    Box(modifier = modifier.fillMaxSize()) {
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
                        onScrollPercentageChanged = { viewModel.onScrollPercentageChanged(it) },
                        onChapterFinished = { viewModel.nextChapter() },
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

            // Toolbar overlay
            AnimatedVisibility(
                visible = isToolbarVisible,
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Column {
                    TopAppBar(
                        title = { Text(book?.title ?: "") },
                        navigationIcon = {
                            IconButton(onClick = { navController?.navigateUp() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                            }
                        },
                        actions = {
                            IconButton(onClick = { isTocVisible = true }) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Table of Contents")
                            }
                        },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            }

            AnimatedVisibility(
                visible = isToolbarVisible,
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Chapter info and progress
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
                            onClick = { viewModel.previousChapter() },
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
                            onClick = { viewModel.nextChapter() },
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
                    ) { viewModel.toggleToolbar() }
            )
        }

        // TOC Bottom Sheet
        if (isTocVisible) {
            ModalBottomSheet(
                onDismissRequest = { isTocVisible = false },
                sheetState = tocSheetState
            ) {
                TocPanel(
                    entries = tocEntries,
                    currentChapterIndex = currentChapterIndex,
                    onChapterSelect = { index ->
                        viewModel.goToChapter(index)
                        isTocVisible = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
