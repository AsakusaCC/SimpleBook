package com.ebookreader.simplebook

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ebookreader.simplebook.di.appModule
import com.ebookreader.simplebook.di.dataModule
import com.ebookreader.simplebook.di.platformModule
import com.ebookreader.simplebook.ui.setupImageLoader
import org.koin.core.context.startKoin
import javax.imageio.ImageIO

fun main() {
    // Koin must be started exactly ONCE at process start. Do NOT put this inside
    // `application {}` — that block is @Composable and recomposes (e.g. when
    // isMinimizedToTray flips on close-to-tray), which would call startKoin a second
    // time and throw KoinApplicationAlreadyStartedException, killing the process.
    startKoin {
        modules(appModule, dataModule, platformModule)
    }
    // 注册全局 Coil ImageLoader（DataUriMapper + SvgDecoder），进程入口一次性完成。
    setupImageLoader()

    application {
        // Closing the window minimizes to tray instead of quitting; the Tray entry
        // (left-click / "打开" menu item) brings it back, "退出" actually exits.
        var isMinimizedToTray by remember { mutableStateOf(false) }

        // Load tray icon from classpath (src/jvmMain/resources/icon.png) via AWT.
        val trayIcon = remember {
            Thread.currentThread().contextClassLoader
                .getResourceAsStream("icon.png")
                ?.use { ImageIO.read(it) }
                ?.toPainter()
        }

        if (trayIcon != null) {
            Tray(
                icon = trayIcon,
                tooltip = "SimpleBook",
                onAction = { isMinimizedToTray = false }
            ) {
                Item("打开 SimpleBook") { isMinimizedToTray = false }
                Separator()
                Item("退出") { exitApplication() }
            }
        }

        val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

        Window(
            onCloseRequest = { isMinimizedToTray = true },
            title = "SimpleBook",
            visible = !isMinimizedToTray,
            state = windowState
        ) {
            // windowState.size is State-driven and updates as the user resizes the window,
            // so App() recomposes and WindowSizeClass adapts (NavigationRail <-> BottomBar).
            App(windowSize = windowState.size)
        }
    }
}
