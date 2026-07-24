package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

/**
 * Android actual: no-op wrapper. Android has no desktop-style OS drag-and-drop, so import
 * is triggered via an SAF ActivityResult launcher (`OpenMultipleDocuments`) in the host
 * Activity, not via this overlay.
 */
@Composable
actual fun DragDropOverlay(
    onFilesDropped: (List<File>) -> Unit,
    modifier: Modifier,
    dropHint: String,
    content: @Composable () -> Unit
) {
    Box(modifier) { content() }
}
