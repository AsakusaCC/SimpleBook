@file:OptIn(ExperimentalComposeUiApi::class)

package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Desktop (JVM) actual: receives OS-level file drag-and-drop via Compose Multiplatform's
 * `Modifier.dragAndDropTarget` (available since CMP 1.7.0; this project targets 1.9.0).
 *
 * Only files with a supported extension (epub/txt) are forwarded to [onFilesDropped].
 * While a supported drag hovers, a semi-transparent overlay with [dropHint] is shown.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun DragDropOverlay(
    onFilesDropped: (List<File>) -> Unit,
    modifier: Modifier,
    dropHint: String,
    content: @Composable () -> Unit
) {
    var isDragOver by remember { mutableStateOf(false) }
    val currentOnDrop by rememberUpdatedState(onFilesDropped)
    val supportedExt = remember { setOf("epub", "txt") }

    val target = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                if (hasSupportedFiles(event, supportedExt)) isDragOver = true
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragOver = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val files = extractSupportedFiles(event, supportedExt)
                isDragOver = false
                if (files.isNotEmpty()) {
                    currentOnDrop(files)
                    return true
                }
                return false
            }
        }
    }

    Box(
        modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> hasFileList(event) },
            target = target
        )
    ) {
        content()
        if (isDragOver) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC1A1A1A))
                    .border(3.dp, MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dropHint.ifBlank { "Drop books here to import" },
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
    }
}

private fun hasFileList(event: DragAndDropEvent): Boolean = try {
    event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
} catch (e: Exception) {
    false
}

private fun extractSupportedFiles(
    event: DragAndDropEvent,
    supportedExt: Set<String>
): List<File> {
    return try {
        val transferable = event.awtTransferable
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
        val data = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*> ?: return emptyList()
        data.filterIsInstance<File>().filter { it.extension.lowercase() in supportedExt }
    } catch (e: Exception) {
        emptyList()
    }
}

/**
 * Returns true if the drag carries at least one supported (epub/txt) file. Reading transfer
 * data during drag-enter can occasionally throw on some platforms, so failures are treated
 * as "no supported files".
 */
private fun hasSupportedFiles(
    event: DragAndDropEvent,
    supportedExt: Set<String>
): Boolean = extractSupportedFiles(event, supportedExt).isNotEmpty()
