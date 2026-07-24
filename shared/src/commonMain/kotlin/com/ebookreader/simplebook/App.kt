package com.ebookreader.simplebook

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.ui.components.AdaptiveScaffold
import com.ebookreader.simplebook.ui.navigation.SimpleBookNavHost
import com.ebookreader.simplebook.ui.settings.SettingsViewModel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import com.ebookreader.simplebook.ui.theme.SimpleBookTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Shared app entry point — used by both Android (MainActivity) and Desktop (Main.kt).
 *
 * Wires up the full UI: theme, NavController, SyncViewModel, AdaptiveScaffold and
 * SimpleBookNavHost. The sign-in launcher triggers OAuth via [SyncViewModel.signIn].
 *
 * Desktop has no Activity, so [calculateWindowSizeClass] (Activity-bound) is not available
 * here. Instead we compute a [WindowSizeClass] from a fixed [DpSize] matching the desktop
 * default window (1200 x 800 dp), which resolves to [WindowWidthSizeClass.Expanded] and
 * yields the NavigationRail-based ExpandedLayout — the right choice for a desktop window.
 * Android callers that need true runtime sizing should continue using their own Activity
 * entry point (MainActivity).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun App() {
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    SimpleBookTheme(readerTheme = settings.theme) {
        Surface(modifier = Modifier.fillMaxSize()) {
            // Desktop has no Activity — derive a fixed WindowSizeClass matching the default
            // 1200 x 800 dp desktop window (resolves to Expanded width -> NavigationRail).
            val windowSizeClass = remember {
                WindowSizeClass.calculateFromSize(DpSize(1200.dp, 800.dp))
            }
            val navController = rememberNavController()
            val syncViewModel: SyncViewModel = koinViewModel()
            val strings = remember(settings.language) { getStrings(settings.language) }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        if (syncViewModel.isSignedIn.value) {
                            syncViewModel.syncNow()
                        }
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            AdaptiveScaffold(
                windowSizeClass = windowSizeClass,
                navController = navController,
                strings = strings
            ) {
                SimpleBookNavHost(
                    navController = navController,
                    windowSizeClass = windowSizeClass,
                    syncViewModel = syncViewModel,
                    signInLauncher = { syncViewModel.signIn() },
                    onReauthClick = { syncViewModel.signIn() }
                )
            }
        }
    }
}
