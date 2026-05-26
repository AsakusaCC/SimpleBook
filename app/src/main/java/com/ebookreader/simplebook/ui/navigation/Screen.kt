package com.ebookreader.simplebook.ui.navigation

sealed class Screen(val route: String) {
    data object BookList : Screen("book_list")
    data object Collection : Screen("collection")
    data object Reader : Screen("reader/{bookId}?charOffset={charOffset}&chapterIndex={chapterIndex}") {
        fun createRoute(bookId: Long, charOffset: Long = 0L, chapterIndex: Int = 0) =
            "reader/$bookId?charOffset=$charOffset&chapterIndex=$chapterIndex"
    }
    data object Bookmark : Screen("bookmark/{bookId}") {
        fun createRoute(bookId: Long) = "bookmark/$bookId"
    }
    data object NoteList : Screen("notes/{bookId}") {
        fun createRoute(bookId: Long) = "notes/$bookId"
    }
    data object Settings : Screen("settings")
    data object Sync : Screen("sync")
}
