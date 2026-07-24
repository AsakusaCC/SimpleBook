package com.ebookreader.simplebook.domain.model

sealed class ShelfItem {
    data class BookItem(val book: Book, val progress: Double) : ShelfItem()
    data class FolderItem(val folder: Folder, val bookCount: Int) : ShelfItem()
}
