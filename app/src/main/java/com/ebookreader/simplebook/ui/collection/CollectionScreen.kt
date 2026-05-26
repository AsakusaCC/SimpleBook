package com.ebookreader.simplebook.ui.collection

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    navController: NavController,
    windowWidthSizeClass: WindowWidthSizeClass,
    viewModel: CollectionViewModel = hiltViewModel()
) {
    when (windowWidthSizeClass) {
        WindowWidthSizeClass.Compact ->
            CollectionCompactLayout(navController, viewModel)
        else ->
            CollectionExpandedLayout(navController, viewModel)
    }
}

// region — Compact Layout (Phone)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionCompactLayout(
    navController: NavController,
    viewModel: CollectionViewModel
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("书签", "笔记")

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("收藏") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
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
                0 -> {
                    val groups by viewModel.bookmarkGroups.collectAsState()
                    BookmarkGroupedList(
                        groups = groups,
                        onItemClick = { bookmark ->
                            navController.navigate(
                                Screen.Reader.createRoute(
                                    bookmark.bookUuid,
                                    bookmark.charOffset,
                                    bookmark.chapterIndex
                                )
                            )
                        },
                        onDelete = { viewModel.deleteBookmark(it) }
                    )
                }
                1 -> {
                    val groups by viewModel.noteGroups.collectAsState()
                    NoteGroupedList(
                        groups = groups,
                        onItemClick = { note ->
                            navController.navigate(
                                Screen.Reader.createRoute(
                                    note.bookUuid,
                                    note.charOffset,
                                    note.chapterIndex
                                )
                            )
                        },
                        onDelete = { viewModel.deleteNote(it) }
                    )
                }
            }
        }
    }
}

// endregion

// region — Expanded Layout (Tablet)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionExpandedLayout(
    navController: NavController,
    viewModel: CollectionViewModel
) {
    val bookmarkGroups by viewModel.bookmarkGroups.collectAsState()
    val noteGroups by viewModel.noteGroups.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("收藏") })
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Text(
                    "书签",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                BookmarkGroupedList(
                    groups = bookmarkGroups,
                    onItemClick = { bookmark ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                bookmark.bookUuid,
                                bookmark.charOffset,
                                bookmark.chapterIndex
                            )
                        )
                    },
                    onDelete = { viewModel.deleteBookmark(it) }
                )
            }
            Spacer(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxSize()
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Text(
                    "笔记",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                NoteGroupedList(
                    groups = noteGroups,
                    onItemClick = { note ->
                        navController.navigate(
                            Screen.Reader.createRoute(
                                note.bookUuid,
                                note.charOffset,
                                note.chapterIndex
                            )
                        )
                    },
                    onDelete = { viewModel.deleteNote(it) }
                )
            }
        }
    }
}

// endregion

// region — Shared Grouped List Components

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarkGroupedList(
    groups: List<GroupedItems<Bookmark>>,
    onItemClick: (Bookmark) -> Unit,
    onDelete: (Bookmark) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyState("还没有书签")
        return
    }
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    groups.forEach { it.book.uuid.let { id -> if (id !in expandedState) expandedState[id] = false } }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groups.forEach { group ->
            val isExpanded = expandedState[group.book.uuid] ?: true
            item(key = "header_${group.book.uuid}") {
                GroupHeader(
                    title = group.book.title,
                    count = group.items.size,
                    isExpanded = isExpanded,
                    onClick = { expandedState[group.book.uuid] = !isExpanded }
                )
            }
            if (isExpanded) {
                items(
                    items = group.items,
                    key = { "bookmark_${it.id}" }
                ) { bookmark ->
                    SwipeToDeleteItem(
                        onDismiss = { onDelete(bookmark) }
                    ) {
                        BookmarkItem(
                            bookmark = bookmark,
                            onClick = { onItemClick(bookmark) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteGroupedList(
    groups: List<GroupedItems<Note>>,
    onItemClick: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    if (groups.isEmpty()) {
        EmptyState("还没有笔记")
        return
    }
    val expandedState = remember { mutableStateMapOf<String, Boolean>() }
    groups.forEach { it.book.uuid.let { id -> if (id !in expandedState) expandedState[id] = false } }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groups.forEach { group ->
            val isExpanded = expandedState[group.book.uuid] ?: true
            item(key = "header_${group.book.uuid}") {
                GroupHeader(
                    title = group.book.title,
                    count = group.items.size,
                    isExpanded = isExpanded,
                    onClick = { expandedState[group.book.uuid] = !isExpanded },
                    icon = Icons.Default.Create
                )
            }
            if (isExpanded) {
                items(
                    items = group.items,
                    key = { "note_${it.id}" }
                ) { note ->
                    SwipeToDeleteItem(
                        onDismiss = { onDelete(note) }
                    ) {
                        NoteItem(
                            note = note,
                            onClick = { onItemClick(note) }
                        )
                    }
                }
            }
        }
    }
}

// endregion

// region — Individual Item Composables

@Composable
private fun GroupHeader(
    title: String,
    count: Int,
    isExpanded: Boolean,
    onClick: () -> Unit,
    icon: ImageVector = Icons.Default.Bookmark
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "($count)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (isExpanded) "收起" else "展开",
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun BookmarkItem(
    bookmark: Bookmark,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bookmark.name.ifBlank { "第 ${bookmark.chapterIndex + 1} 章" },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatRelativeTime(bookmark.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NoteItem(
    note: Note,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .padding(start = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = note.content.take(80),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatRelativeTime(note.createdAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                MaterialTheme.colorScheme.error
            } else {
                Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = color
                )
            }
        },
        enableDismissFromStartToEnd = false
    ) {
        content()
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// endregion

// region — Utility

private fun formatRelativeTime(timestamp: Long): String {
    val diff = maxOf(0L, System.currentTimeMillis() - timestamp)
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L}分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L}小时前"
        diff < 604_800_000L -> "${diff / 86_400_000L}天前"
        else -> "${diff / 604_800_000L}周前"
    }
}

// endregion
