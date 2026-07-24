package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ebookreader.simplebook.domain.model.AppStrings
import com.ebookreader.simplebook.domain.model.CollectionIcon
import com.ebookreader.simplebook.ui.navigation.Screen
import com.ebookreader.simplebook.ui.theme.LocalReaderTheme
import org.jetbrains.compose.resources.painterResource
import simplebook.shared.generated.resources.Res
import simplebook.shared.generated.resources.ic_diamond
import simplebook.shared.generated.resources.ic_flower
import simplebook.shared.generated.resources.ic_wave

// 自定义主题图标（樱花/钻石/海浪）从 shared composeResources 加载，恢复 KMP 迁移前的原始矢量图。
// 其余主题仍用 Material 图标，统一包成 Painter。
@Composable
private fun collectionIconPainter(icon: CollectionIcon): Painter = when (icon) {
    CollectionIcon.HEART -> rememberVectorPainter(Icons.Default.Favorite)
    CollectionIcon.BOOKMARK -> rememberVectorPainter(Icons.Default.Bookmark)
    CollectionIcon.FLOWER -> painterResource(Res.drawable.ic_flower)
    CollectionIcon.LEAF -> rememberVectorPainter(Icons.Default.Eco)
    CollectionIcon.DIAMOND -> painterResource(Res.drawable.ic_diamond)
    CollectionIcon.MOON -> rememberVectorPainter(Icons.Default.ModeNight)
    CollectionIcon.SPROUT -> rememberVectorPainter(Icons.Default.Yard)
    CollectionIcon.WAVE -> painterResource(Res.drawable.ic_wave)
}

@Composable
fun AdaptiveScaffold(
    windowSizeClass: WindowSizeClass,
    navController: NavHostController,
    strings: AppStrings,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showNav = currentRoute != Screen.Reader.route

    val collectionIcon = collectionIconPainter(LocalReaderTheme.current.collectionIcon)
    val navItems = listOf(
        NavItem(Screen.BookList.route, rememberVectorPainter(Icons.Default.Book), strings.navBooks),
        NavItem(Screen.Collection.route, collectionIcon, strings.navFavorites),
        NavItem(Screen.Settings.route, rememberVectorPainter(Icons.Default.Settings), strings.navSettings),
    )

    when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Compact -> {
            CompactLayout(navController = navController, navItems = navItems, showNav = showNav, content = content)
        }
        else -> {
            ExpandedLayout(navController = navController, navItems = navItems, showNav = showNav, content = content)
        }
    }
}

private data class NavItem(
    val route: String,
    val icon: Painter,
    val label: String
)

@Composable
private fun CompactLayout(
    navController: NavHostController,
    navItems: List<NavItem>,
    showNav: Boolean,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showNav) {
                FloatingBottomNavBar(
                    navItems = navItems,
                    currentDestination = currentDestination,
                    onNavigate = { route ->
                        navController.navigate(route) {
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
    ) { innerPadding ->
        if (showNav) {
            Box(modifier = Modifier.padding(innerPadding)) {
                content()
            }
        } else {
            content()
        }
    }
}

@Composable
private fun FloatingBottomNavBar(
    navItems: List<NavItem>,
    currentDestination: androidx.navigation.NavDestination?,
    onNavigate: (String) -> Unit
) {
    val isDark = LocalReaderTheme.current.isDark
    val barShape = RoundedCornerShape(16.dp)

    Surface(
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 2.dp)
                .shadow(
                    elevation = 6.dp,
                    shape = barShape,
                    ambientColor = Color.Black.copy(alpha = 0.12f),
                    spotColor = Color.Black.copy(alpha = 0.06f)
                )
                .clip(barShape)
                .background(
                    if (isDark) Color(0xFF2A2A2E) else Color.White
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                navItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        selected = selected,
                        onClick = { onNavigate(item.route) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpandedLayout(
    navController: NavHostController,
    navItems: List<NavItem>,
    showNav: Boolean,
    content: @Composable () -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Row(modifier = Modifier.fillMaxSize()) {
        if (showNav) {
            NavigationRail {
                navItems.forEach { item ->
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
        }
        content()
    }
}
