package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun TxtReaderView(
    paragraphs: List<String>,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onScrollPositionChanged: (Float) -> Unit,
    onTap: () -> Unit = {},
    hasNextChapter: Boolean = false,
    onNextChapter: () -> Unit = {},
    nextChapterText: String = "Next Chapter",
    endOfChapterTitle: String = "End of Chapter",
    continueQuestionText: String = "Continue to the next chapter?",
    continueBtnText: String = "Continue",
    stayBtnText: String = "Stay",
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var hasNotifiedEnd by remember(paragraphs) { mutableStateOf(false) }
    var showEndDialog by remember { mutableStateOf(false) }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(onTap) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitPointerEvent(PointerEventPass.Initial)
                    val downChange = down.changes.firstOrNull()
                    if (downChange == null || !downChange.pressed) continue
                    val downPos = downChange.position
                    var isTap = true
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) {
                            if (isTap) {
                                val dx = change.position.x - downPos.x
                                val dy = change.position.y - downPos.y
                                if (kotlin.math.sqrt(dx * dx + dy * dy) < viewConfiguration.touchSlop) {
                                    onTap()
                                }
                            }
                            break
                        }
                        if (change.isConsumed) isTap = false
                    }
                }
            }
        }
    ) {
        items(paragraphs) { paragraph ->
            Text(
                text = paragraph,
                style = textStyle,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (hasNextChapter) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onNextChapter) {
                        Text(nextChapterText, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }
            .collect { canScroll ->
                if (!canScroll && hasNextChapter && !hasNotifiedEnd) {
                    hasNotifiedEnd = true
                    showEndDialog = true
                }
            }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text(endOfChapterTitle) },
            text = { Text(continueQuestionText) },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    onNextChapter()
                }) {
                    Text(continueBtnText)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text(stayBtnText)
                }
            }
        )
    }

    LaunchedEffect(listState, paragraphs) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                val pct = if (paragraphs.isNotEmpty()) {
                    index.toFloat() / paragraphs.size.toFloat()
                } else 0f
                onScrollPositionChanged(pct)
            }
    }
}
