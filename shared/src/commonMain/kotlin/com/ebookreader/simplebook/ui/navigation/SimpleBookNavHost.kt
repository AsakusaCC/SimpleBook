package com.ebookreader.simplebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.platform.ReauthRequest
import com.ebookreader.simplebook.ui.collection.CollectionScreen
import com.ebookreader.simplebook.ui.booklist.BookListScreen
import com.ebookreader.simplebook.ui.bookmark.BookmarkScreen
import com.ebookreader.simplebook.ui.note.NoteScreen
import com.ebookreader.simplebook.ui.reader.ReaderScreen
import com.ebookreader.simplebook.ui.settings.SettingsScreen
import com.ebookreader.simplebook.ui.sync.SyncLogScreen
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun SimpleBookNavHost(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    syncViewModel: SyncViewModel? = null,
    signInLauncher: (() -> Unit)? = null,
    onReauthClick: ((ReauthRequest) -> Unit)? = null,
    onImportClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Share ONE Activity-scoped SyncViewModel across all destinations. Without this, each
    // destination's koinViewModel() resolves its OWN NavBackStackEntry-scoped instance, so the
    // sign-in callback (which refreshes the MainActivity-held instance) wouldn't update the
    // instance SettingsScreen reads → "nothing happens" after picking a Google account.
    val svm: SyncViewModel = syncViewModel ?: koinViewModel()

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
                syncViewModel = svm,
                onBookClick = { book: Book ->
                    navController.navigate(Screen.Reader.createRoute(book.uuid))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onImportClick = onImportClick
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookUuid") { type = NavType.StringType },
                navArgument("charOffset") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("chapterIndex") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val bookUuid = backStackEntry.savedStateHandle.get<String>("bookUuid") ?: ""
            ReaderScreen(
                bookUuid = bookUuid,
                navController = navController,
                windowWidthSizeClass = windowSizeClass.widthSizeClass
            )
        }

        composable(
            route = Screen.Bookmark.route,
            arguments = listOf(
                navArgument("bookUuid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookUuid = backStackEntry.savedStateHandle.get<String>("bookUuid") ?: ""
            BookmarkScreen(bookUuid = bookUuid)
        }

        composable(
            route = Screen.NoteList.route,
            arguments = listOf(
                navArgument("bookUuid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookUuid = backStackEntry.savedStateHandle.get<String>("bookUuid") ?: ""
            NoteScreen(bookUuid = bookUuid)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                onSignInClick = signInLauncher,
                onReauthClick = onReauthClick,
                syncViewModel = svm
            )
        }

        composable(Screen.SyncLog.route) {
            SyncLogScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
