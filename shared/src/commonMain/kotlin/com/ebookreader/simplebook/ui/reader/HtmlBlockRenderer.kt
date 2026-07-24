package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * 渲染单个 [HtmlBlock]。无状态，由 [EpubReaderView] 在 LazyColumn 中逐项调用。
 */
@Composable
fun HtmlBlockRenderer(
    block: HtmlBlock,
    baseTextStyle: TextStyle,
    textColor: Color,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    when (block) {
        is HtmlBlock.Heading -> Text(
            text = block.content.toAnnotatedString(accentColor),
            style = baseTextStyle.copy(
                fontSize = (baseTextStyle.fontSize.value * headingScale(block.level)).sp,
                fontWeight = FontWeight.Bold
            ),
            color = textColor,
            modifier = modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        is HtmlBlock.Paragraph -> Text(
            text = block.content.toAnnotatedString(accentColor),
            style = baseTextStyle,
            color = textColor,
            modifier = modifier.padding(vertical = 4.dp)
        )

        is HtmlBlock.Image -> BlockImage(block, modifier)

        is HtmlBlock.Quote -> Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .drawBehind {
                    drawLine(
                        color = accentColor,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 3.dp.toPx()
                    )
                }
        ) {
            Text(
                text = block.content.toAnnotatedString(accentColor),
                style = baseTextStyle.copy(fontStyle = FontStyle.Italic),
                color = textColor,
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        is HtmlBlock.ListItem -> {
            val prefix = if (block.ordered) "${block.index}. " else "• "
            Row(modifier = modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(prefix, style = baseTextStyle, color = textColor)
                Text(
                    text = block.content.toAnnotatedString(accentColor),
                    style = baseTextStyle,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        is HtmlBlock.CodeBlock -> Text(
            text = block.text,
            style = baseTextStyle.copy(fontFamily = FontFamily.Monospace),
            color = textColor,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(textColor.copy(alpha = 0.05f))
                .padding(8.dp)
        )

        HtmlBlock.Rule -> HorizontalDivider(
            modifier = modifier.padding(vertical = 12.dp),
            thickness = 1.dp,
            color = textColor.copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun BlockImage(block: HtmlBlock.Image, modifier: Modifier) {
    if (isSvgDataUri(block.src)) {
        // SVG 在桌面无原生解码器（Coil 默认不含 coil-svg 扩展）：显示 alt，否则留空
        if (!block.alt.isNullOrBlank()) {
            Text(
                text = block.alt,
                style = TextStyle(color = Color.Gray),
                modifier = modifier.padding(8.dp)
            )
        }
    } else {
        // Coil3 原生支持 data: URI model，跨平台解码（png/jpeg/gif/webp）
        AsyncImage(
            model = block.src,
            contentDescription = block.alt,
            modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
    }
}

private fun isSvgDataUri(src: String): Boolean =
    src.startsWith("data:image/svg", ignoreCase = true)

private fun InlineContent.toAnnotatedString(linkColor: Color) = buildAnnotatedString {
    for (span in spans) {
        val style = SpanStyle(
            fontWeight = if (span.bold) FontWeight.Bold else null,
            fontStyle = if (span.italic) FontStyle.Italic else null,
            fontFamily = if (span.code) FontFamily.Monospace else null,
            color = if (span.href != null) linkColor else Color.Unspecified,
            textDecoration = if (span.href != null) TextDecoration.Underline else null
        )
        withStyle(style) { append(span.text) }
    }
}

private fun headingScale(level: Int): Float = when (level) {
    1 -> 1.6f
    2 -> 1.4f
    3 -> 1.2f
    4 -> 1.1f
    else -> 1.0f
}
