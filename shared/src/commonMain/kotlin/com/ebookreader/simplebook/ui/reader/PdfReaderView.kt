package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.data.parser.PdfPageLoader
import com.ebookreader.simplebook.data.parser.coercePdfPage
import kotlinx.coroutines.flow.distinctUntilChanged

/** PDF 阅读会话状态：页数、加载器、后台预扫出的每页宽高比（高/宽）。 */
data class PdfReaderState(
    val pageCount: Int,
    val loader: PdfPageLoader,
    val aspectRatios: List<Float> = emptyList()
)

/**
 * PDF 纵向连续滚动阅读视图。
 * - 适宽渲染（视口宽 × [QUALITY_SCALE]）+ 双击切换 2x 档（单页内横向滚动）；
 * - 单击切换工具栏；当前页 = 首可见 item，经 [onPageChanged] 上报；
 * - [initialPage] 变化（TOC 跳页/进度恢复）时滚动到目标页。
 */
@Composable
fun PdfReaderView(
    state: PdfReaderState,
    initialPage: Int,
    pageLabel: (page: Int) -> String,
    pageLoadFailedText: String,
    onPageChanged: (Int) -> Unit,
    onTap: () -> Unit,
    backgroundColor: Long,
    modifier: Modifier = Modifier
) {
    var zoomed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // 响应初始页恢复与外部跳页（TOC/书签/笔记）。
    // 注意：不能用 snapshotFlow 观察普通参数 initialPage（非 snapshot state，
    // 只在启动时发射一次）；把 initialPage 放进 effect key，参数变化重启 effect
    // 触发滚动（与 EpubReaderView 的 LaunchedEffect(blocks) 恢复模式一致）。
    LaunchedEffect(listState, state, initialPage) {
        val target = coercePdfPage(initialPage, state.pageCount)
        if (target != listState.firstVisibleItemIndex) {
            listState.scrollToItem(target)
        }
    }

    // 当前页上报（首可见 item）；coercePdfPage 兜底 pageCount 为 0 的空区间
    LaunchedEffect(listState, state.pageCount) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { page -> onPageChanged(coercePdfPage(page, state.pageCount)) }
    }

    BoxWithConstraints(modifier = modifier.background(Color(backgroundColor))) {
        val viewportPx = with(LocalDensity.current) { maxWidth.toPx() }
        val renderWidthPx = ((if (zoomed) viewportPx * 2f else viewportPx) * QUALITY_SCALE)
            .toInt()
            .coerceAtMost(MAX_RENDER_WIDTH_PX)

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { zoomed = !zoomed }
                    )
                }
        ) {
            items(state.pageCount, key = { it }) { index ->
                PdfPageItem(
                    index = index,
                    aspectRatio = state.aspectRatios.getOrNull(index)
                        ?: PdfPageLoader.DEFAULT_ASPECT_RATIO,
                    renderWidthPx = renderWidthPx,
                    zoomed = zoomed,
                    viewportWidthPx = viewportPx,
                    loader = state.loader,
                    pageLabel = pageLabel,
                    pageLoadFailedText = pageLoadFailedText
                )
            }
        }
    }
}

/** 单页加载三态：渲染失败（文档关闭/坏页/OOM）单独呈现，不阻塞其他页。 */
private sealed interface PageState {
    data object Loading : PageState
    data class Ready(val bitmap: ImageBitmap) : PageState
    data object Failed : PageState
}

@Composable
private fun PdfPageItem(
    index: Int,
    aspectRatio: Float,
    renderWidthPx: Int,
    zoomed: Boolean,
    viewportWidthPx: Float,
    loader: PdfPageLoader,
    pageLabel: (page: Int) -> String,
    pageLoadFailedText: String
) {
    val pageState by produceState<PageState>(PageState.Loading, index, renderWidthPx) {
        val bmp = loader.load(index, renderWidthPx)
        value = if (bmp != null) PageState.Ready(bmp) else PageState.Failed
        // 预取下一页，滚动时直接命中缓存
        if (bmp != null) loader.load(index + 1, renderWidthPx)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pageContent: @Composable (Modifier) -> Unit = { m ->
            Box(m.aspectRatio(aspectRatio).background(Color.White)) {
                when (val ps = pageState) {
                    is PageState.Ready -> Image(
                        bitmap = ps.bitmap,
                        contentDescription = pageLabel(index + 1),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    PageState.Loading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                    PageState.Failed -> Text(
                        text = pageLoadFailedText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        if (zoomed) {
            // 2x 档：页面宽于视口，单页内横向滚动
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                pageContent(
                    Modifier.requiredWidth(
                        with(LocalDensity.current) { (viewportWidthPx * 2f).toDp() }
                    )
                )
            }
        } else {
            pageContent(Modifier.fillMaxWidth())
        }
        Text(
            text = pageLabel(index + 1),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

private const val QUALITY_SCALE = 1.6f        // 适宽渲染的超采样系数（清晰度）
private const val MAX_RENDER_WIDTH_PX = 2400 // 位图宽度上限（内存护栏）
