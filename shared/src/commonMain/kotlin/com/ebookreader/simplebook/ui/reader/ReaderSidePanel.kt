package com.ebookreader.simplebook.ui.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.TocEntry

@Composable
fun ReaderSidePanel(
    tocEntries: List<TocEntry>,
    bookmarks: List<Bookmark>,
    notes: List<Note>,
    currentChapterIndex: Int,
    onChapterSelect: (Int) -> Unit,
    onBookmarkClick: (Bookmark) -> Unit,
    onNoteClick: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Contents", "Bookmarks", "Notes")

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab) {
            0 -> TocPanel(
                entries = tocEntries,
                currentChapterIndex = currentChapterIndex,
                onChapterSelect = onChapterSelect,
                modifier = Modifier.fillMaxSize()
            )
            1 -> BookmarksPanel(
                bookmarks = bookmarks,
                onBookmarkClick = onBookmarkClick,
                modifier = Modifier.fillMaxSize()
            )
            2 -> NotesPanel(
                notes = notes,
                onNoteClick = onNoteClick,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun BookmarksPanel(
    bookmarks: List<Bookmark>,
    onBookmarkClick: (Bookmark) -> Unit,
    modifier: Modifier = Modifier
) {
    if (bookmarks.isEmpty()) {
        Text("No bookmarks", modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn(modifier = modifier) {
            items(bookmarks.size) { index ->
                val bookmark = bookmarks[index]
                Text(
                    text = bookmark.name.ifBlank { "Ch ${bookmark.chapterIndex + 1}" },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onBookmarkClick(bookmark) }
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun NotesPanel(
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    modifier: Modifier = Modifier
) {
    if (notes.isEmpty()) {
        Text("No notes", modifier = Modifier.padding(16.dp))
    } else {
        LazyColumn(modifier = modifier) {
            items(notes.size) { index ->
                val note = notes[index]
                Text(
                    text = note.content.take(100),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNoteClick(note) }
                        .padding(16.dp)
                )
            }
        }
    }
}
