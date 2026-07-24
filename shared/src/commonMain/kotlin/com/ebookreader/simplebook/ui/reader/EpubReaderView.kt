package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sqrt

@Composable
fun EpubReaderView(
    htmlContent: String,
    initialScrollPercentage: Float = 0f,
    onScrollPercentageChanged: (Float) -> Unit,
    onChapterFinished: () -> Unit,
    backgroundColor: Long = 0xFFFFFFFF,
    textColor: Long = 0xFF000000,
    accentColor: Long = 0xFF6750A4,
    fontSize: Float = 16f,
    lineHeight: Float = 1.5f,
    hasNextChapter: Boolean = true,
    nextChapterText: String = "下一章 →",
    allReadText: String = "已读全部章节",
    onTap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val blocks = remember(htmlContent) { HtmlBlockParser.parse(htmlContent) }
    val listState = rememberLazyListState()
    var showEndDialog by remember { mutableStateOf(false) }
    var hasNotifiedEnd by remember(blocks) { mutableStateOf(false) }

    val baseTextStyle = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize * lineHeight).sp,
        color = Color(textColor)
    )
    val fg = Color(textColor)
    val accent = Color(accentColor)

    val totalItems = (blocks.size + 1).coerceAtLeast(1)

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
                                if (sqrt(dx * dx + dy * dy) < viewConfiguration.touchSlop) {
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
        itemsIndexed(blocks) { _, block ->
            HtmlBlockRenderer(
                block = block,
                baseTextStyle = baseTextStyle,
                textColor = fg,
                accentColor = accent,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (hasNextChapter) {
                    TextButton(onClick = onChapterFinished) {
                        Text(nextChapterText, color = accent)
                    }
                } else {
                    Text(allReadText, color = fg, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    // 进度百分比（基于首可见 item 索引）
    LaunchedEffect(listState, blocks) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { index ->
                val pct = (index.toFloat() / totalItems).coerceIn(0f, 1f)
                onScrollPercentageChanged(pct)
            }
    }

    // 章末：内容曾可滚动（图片已加载且超过视口）→ 用户滚到底，才弹「下一章」确认。
    // 不能只看 canScrollForward==false：纯图片页首帧图片尚未异步解码、内容高度≈0，
    // canScrollForward 一开始就是 false，snapshotFlow 首帧即发射，会立即误触发弹窗。
    // 这里要求经历 true→false 的转换，确保是“用户真的滚到了底”。
    LaunchedEffect(listState, blocks) {
        var wasScrollable = false
        snapshotFlow { listState.canScrollForward }
            .collect { canScroll ->
                if (!canScroll && wasScrollable && hasNextChapter && !hasNotifiedEnd) {
                    hasNotifiedEnd = true
                    showEndDialog = true
                }
                wasScrollable = canScroll
            }
    }

    // 初始 / 切章恢复滚动位置
    LaunchedEffect(blocks) {
        if (initialScrollPercentage > 0f && blocks.isNotEmpty()) {
            val target = (initialScrollPercentage * totalItems).toInt().coerceIn(0, blocks.size)
            listState.scrollToItem(target)
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("到达本章末尾") },
            text = { Text("是否继续下一章?") },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    onChapterFinished()
                }) { Text(nextChapterText) }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("留在此页") }
            }
        )
    }
}
