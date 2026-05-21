package com.ebookreader.simplebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ebookreader.simplebook.ui.booklist.BookListScreen
import com.ebookreader.simplebook.ui.bookmark.BookmarkScreen
import com.ebookreader.simplebook.ui.note.NoteScreen
import com.ebookreader.simplebook.ui.reader.ReaderScreen
import com.ebookreader.simplebook.ui.settings.SettingsScreen

@Composable
fun SimpleBookNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.BookList.route,
        modifier = modifier
    ) {
        composable(Screen.BookList.route) {
            BookListScreen()
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
            ReaderScreen(bookId = bookId)
        }

        composable(
            route = Screen.Bookmark.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
            BookmarkScreen(bookId = bookId)
        }

        composable(
            route = Screen.NoteList.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
            NoteScreen(bookId = bookId)
        }

        composable(Screen.Settings.route) {
            SettingsScreen()
        }
    }
}
