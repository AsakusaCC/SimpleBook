package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.domain.model.TocEntry

@Composable
fun TocPanel(
    entries: List<TocEntry>,
    currentChapterIndex: Int,
    onChapterSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(entries) { entry ->
            TocItem(
                entry = entry,
                currentChapterIndex = currentChapterIndex,
                onChapterSelect = onChapterSelect,
                depth = 0
            )
        }
    }
}

@Composable
private fun TocItem(
    entry: TocEntry,
    currentChapterIndex: Int,
    onChapterSelect: (Int) -> Unit,
    depth: Int
) {
    val isSelected = entry.chapterIndex == currentChapterIndex
    val textStyle = if (isSelected) {
        MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        MaterialTheme.typography.bodyMedium
    }

    Column {
        Text(
            text = entry.title,
            style = textStyle,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = entry.chapterIndex >= 0) {
                    onChapterSelect(entry.chapterIndex)
                }
                .padding(
                    start = (16 + depth * 24).dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 12.dp
                )
        )

        // Render children with increased indentation
        entry.children.forEach { child ->
            TocItem(
                entry = child,
                currentChapterIndex = currentChapterIndex,
                onChapterSelect = onChapterSelect,
                depth = depth + 1
            )
        }
    }
}
