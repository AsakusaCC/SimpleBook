package com.ebookreader.simplebook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

/**
 * Wraps [content] so the user can drop epub/txt files to import them.
 *
 * - **Desktop (JVM)**: real implementation using Compose Multiplatform's
 *   `Modifier.dragAndDropTarget` — accepts `java.awt` file-list drags and shows a
 *   highlight overlay (with [dropHint]) while a supported drag is in progress.
 * - **Android**: no-op wrapper (just renders [content]); Android has no desktop-style
 *   drag-and-drop, so import goes through an SAF `OpenMultipleDocuments` ActivityResult
 *   contract.
 *
 * `onFilesDropped` receives the supported files (epub/txt only); the platform actual is
 * responsible for filtering.
 *
 * @param dropHint Localized text shown inside the desktop highlight overlay.
 */
@Composable
expect fun DragDropOverlay(
    onFilesDropped: (List<File>) -> Unit,
    modifier: Modifier = Modifier,
    dropHint: String = "",
    content: @Composable () -> Unit
)
