package com.ebookreader.simplebook.ui.navigation

sealed class Screen(val route: String) {
    data object BookList : Screen("book_list")
    data object Collection : Screen("collection")
    data object Reader : Screen("reader/{bookUuid}?charOffset={charOffset}&chapterIndex={chapterIndex}") {
        fun createRoute(bookUuid: String, charOffset: Long = 0L, chapterIndex: Int = -1) =
            "reader/$bookUuid?charOffset=$charOffset&chapterIndex=$chapterIndex"
    }
    data object Bookmark : Screen("bookmark/{bookUuid}") {
        fun createRoute(bookUuid: String) = "bookmark/$bookUuid"
    }
    data object NoteList : Screen("notes/{bookUuid}") {
        fun createRoute(bookUuid: String) = "notes/$bookUuid"
    }
    data object Settings : Screen("settings")
    data object SyncLog : Screen("sync_log")
}