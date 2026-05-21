package com.ebookreader.simplebook.ui.navigation

sealed class Screen(val route: String) {
    data object BookList : Screen("book_list")
    data object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: Long) = "reader/$bookId"
    }
    data object Bookmark : Screen("bookmark/{bookId}") {
        fun createRoute(bookId: Long) = "bookmark/$bookId"
    }
    data object NoteList : Screen("notes/{bookId}") {
        fun createRoute(bookId: Long) = "notes/$bookId"
    }
    data object Settings : Screen("settings")
}
