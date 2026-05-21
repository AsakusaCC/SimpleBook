package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ebookreader.simplebook.ui.navigation.Screen

/**
 * Adaptive layout component that switches between bottom navigation and navigation rail
 * based on the current window size class.
 *
 * - Compact width: standard Scaffold with bottom navigation bar
 * - Expanded width: Row layout with navigation rail on the left
 */
@Composable
fun AdaptiveScaffold(
    windowSizeClass: WindowSizeClass,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            CompactLayout(navController = navController, content = content)
        }
        else -> {
            ExpandedLayout(navController = navController, content = content)
        }
    }
}

private data class NavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

private val navigationItems = listOf(
    NavItem(
        route = Screen.BookList.route,
        icon = Icons.Default.Book,
        label = "Books"
    ),
    NavItem(
        route = Screen.BookList.route,
        icon = Icons.Default.Favorite,
        label = "Favorites"
    ),
    NavItem(
        route = Screen.Settings.route,
        icon = Icons.Default.Settings,
        label = "Settings"
    )
)

@Composable
private fun CompactLayout(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navigationItems.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        content()
    }
}

@Composable
private fun ExpandedLayout(
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Row(modifier = Modifier.fillMaxSize()) {
        NavigationRail {
            navigationItems.forEach { item ->
                NavigationRailItem(
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
        content()
    }
}
