package com.ebookreader.simplebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.ui.collection.CollectionScreen
import com.ebookreader.simplebook.ui.booklist.BookListScreen
import com.ebookreader.simplebook.ui.bookmark.BookmarkScreen
import com.ebookreader.simplebook.ui.note.NoteScreen
import com.ebookreader.simplebook.ui.reader.ReaderScreen
import com.ebookreader.simplebook.ui.settings.SettingsScreen
import com.ebookreader.simplebook.ui.sync.SyncScreen
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun SimpleBookNavHost(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    syncViewModel: SyncViewModel? = null,
    signInLauncher: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.BookList.route,
        modifier = modifier
    ) {
        composable(Screen.Collection.route) {
            CollectionScreen(
                navController = navController,
                windowWidthSizeClass = windowSizeClass.widthSizeClass
            )
        }

        composable(Screen.BookList.route) {
            BookListScreen(
                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                onBookClick = { book: Book ->
                    navController.navigate(Screen.Reader.createRoute(book.id))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.LongType },
                navArgument("charOffset") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("chapterIndex") {
                    type = NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
            ReaderScreen(
                bookId = bookId,
                navController = navController,
                windowWidthSizeClass = windowSizeClass.widthSizeClass
            )
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
            SettingsScreen(
                navController = navController,
                onSignInClick = signInLauncher
            )
        }

        composable(Screen.Sync.route) {
            SyncScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
