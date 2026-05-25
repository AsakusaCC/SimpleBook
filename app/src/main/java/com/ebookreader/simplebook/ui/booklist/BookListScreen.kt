package com.ebookreader.simplebook.ui.booklist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.ui.components.AdaptiveBookGrid

@Composable
fun BookListScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    onBookClick: (Book) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookListViewModel = hiltViewModel()
) {
    val books by viewModel.books.collectAsState()
    val bookProgress by viewModel.bookProgress.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }
    var bookToDelete by remember { mutableStateOf<Book?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromUris(uris)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (books.isEmpty()) {
            Text(
                text = strings.noContent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            AdaptiveBookGrid(
                books = books,
                windowWidthSizeClass = windowWidthSizeClass,
                onBookClick = onBookClick,
                onBookLongClick = { book ->
                    bookToDelete = book
                },
                unknownAuthorText = strings.unknownAuthor,
                bookProgress = bookProgress
            )
        }

        FloatingActionButton(
            onClick = {
                importLauncher.launch(
                    arrayOf("application/epub+zip", "text/plain")
                )
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = strings.importBook
            )
        }
    }

    bookToDelete?.let { book ->
        AlertDialog(
            onDismissRequest = { bookToDelete = null },
            title = { Text(strings.deleteBook) },
            text = { Text(strings.deleteMessage(book.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(book)
                        bookToDelete = null
                    }
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { bookToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
