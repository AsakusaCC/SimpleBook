package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.domain.model.Book

@Composable
fun AdaptiveBookGrid(
    books: List<Book>,
    windowWidthSizeClass: WindowWidthSizeClass,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    unknownAuthorText: String = "未知",
    bookProgress: Map<Long, Double> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val columns = when (windowWidthSizeClass) {
        WindowWidthSizeClass.Compact -> 2
        WindowWidthSizeClass.Medium -> 3
        else -> 4
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(
                book = book,
                onClick = { onBookClick(book) },
                onLongClick = { onBookLongClick(book) },
                unknownAuthorText = unknownAuthorText,
                percentage = bookProgress[book.id] ?: 0.0
            )
        }
    }
}
