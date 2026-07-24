package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.Folder
import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ShelfItem

@Composable
fun AdaptiveBookGrid(
    items: List<ShelfItem>,
    layoutMode: LayoutMode = LayoutMode.LARGE_GRID,
    windowWidthSizeClass: WindowWidthSizeClass,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onFolderClick: (Folder) -> Unit,
    onFolderLongClick: (Folder) -> Unit = {},
    unknownAuthorText: String = "未知",
    bookCountText: (Int) -> String = { "共 $it 本" },
    modifier: Modifier = Modifier
) {
    val isCompact = windowWidthSizeClass == WindowWidthSizeClass.Compact

    if (layoutMode == LayoutMode.LIST) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { when (it) {
                is ShelfItem.BookItem -> "book_${it.book.uuid}"
                is ShelfItem.FolderItem -> "folder_${it.folder.uuid}"
            }}) { item ->
                when (item) {
                    is ShelfItem.BookItem -> BookListItem(
                        book = item.book,
                        onClick = { onBookClick(item.book) },
                        onLongClick = { onBookLongClick(item.book) },
                        unknownAuthorText = unknownAuthorText,
                        percentage = item.progress
                    )
                    is ShelfItem.FolderItem -> FolderListItem(
                        folder = item.folder,
                        bookCount = item.bookCount,
                        bookCountText = bookCountText(item.bookCount),
                        onClick = { onFolderClick(item.folder) },
                        onLongClick = { onFolderLongClick(item.folder) }
                    )
                }
            }
        }
    } else {
        val columns = when {
            layoutMode == LayoutMode.LARGE_GRID && isCompact -> 2
            layoutMode == LayoutMode.LARGE_GRID -> 4
            layoutMode == LayoutMode.SMALL_GRID && isCompact -> 3
            else -> 6
        }
        val isSmall = layoutMode == LayoutMode.SMALL_GRID

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (isSmall) 12.dp else 16.dp,
                vertical = if (isSmall) 8.dp else 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isSmall) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSmall) 10.dp else 16.dp)
        ) {
            items(items, key = { when (it) {
                is ShelfItem.BookItem -> "book_${it.book.uuid}"
                is ShelfItem.FolderItem -> "folder_${it.folder.uuid}"
            }}) { item ->
                when (item) {
                    is ShelfItem.BookItem -> BookCard(
                        book = item.book,
                        onClick = { onBookClick(item.book) },
                        onLongClick = { onBookLongClick(item.book) },
                        unknownAuthorText = unknownAuthorText,
                        percentage = item.progress,
                        compact = isSmall
                    )
                    is ShelfItem.FolderItem -> FolderCard(
                        folder = item.folder,
                        bookCount = item.bookCount,
                        bookCountText = bookCountText(item.bookCount),
                        onClick = { onFolderClick(item.folder) },
                        onLongClick = { onFolderLongClick(item.folder) },
                        compact = isSmall
                    )
                }
            }
        }
    }
}
