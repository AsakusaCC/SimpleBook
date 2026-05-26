# 收藏页实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 实现收藏页面，聚合展示所有书籍的书签和笔记，支持点击跳转、左滑删除、手机/Pad 自适应布局。

**架构：** 新增 `ui/collection/` 包含 CollectionScreen + CollectionViewModel。CollectionScreen 根据 WindowSizeClass 切换手机端（TabRow 单列）和 Pad 端（双列并排）。CollectionViewModel 通过 combine 聚合书签/笔记/书籍数据并按书分组。扩展 Reader 路由支持 charOffset/chapterIndex 位置跳转。

**技术栈：** Jetpack Compose, Hilt, Room, Navigation Compose, Material3, Kotlin Coroutines + Flow

**规格文档：** `docs/superpowers/specs/2026-05-26-collection-bookmarks-notes-design.md`

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `ui/navigation/Screen.kt` | 新增 Collection 路由，扩展 Reader 路由 |
| 修改 | `domain/service/BookmarkService.kt` | 添加 deleteBookmark(Bookmark) 方法 |
| 创建 | `ui/collection/CollectionViewModel.kt` | 聚合书签/笔记/书籍，按书分组 |
| 创建 | `ui/collection/CollectionScreen.kt` | 收藏页 UI（手机/Pad 自适应） |
| 修改 | `ui/navigation/SimpleBookNavHost.kt` | 注册 collection composable，Reader 路由增加参数 |
| 修改 | `ui/components/AdaptiveScaffold.kt` | 收藏按钮路由指向 Collection |
| 修改 | `ui/reader/ReaderViewModel.kt` | 读取位置参数，覆盖初始章节 |

---

### 任务 1：扩展导航路由定义

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/navigation/Screen.kt`

- [ ] **步骤 1：修改 Screen.kt**

在 `Screen.kt` 中新增 `Collection` 路由，扩展 `Reader` 路由添加位置参数：

```kotlin
package com.ebookreader.simplebook.ui.navigation

sealed class Screen(val route: String) {
    data object BookList : Screen("book_list")
    data object Collection : Screen("collection")
    data object Reader : Screen("reader/{bookId}?charOffset={charOffset}&chapterIndex={chapterIndex}") {
        fun createRoute(bookId: Long, charOffset: Long = 0L, chapterIndex: Int = 0) =
            "reader/$bookId?charOffset=$charOffset&chapterIndex=$chapterIndex"
    }
    data object Bookmark : Screen("bookmark/{bookId}") {
        fun createRoute(bookId: Long) = "bookmark/$bookId"
    }
    data object NoteList : Screen("notes/{bookId}") {
        fun createRoute(bookId: Long) = "notes/$bookId"
    }
    data object Settings : Screen("settings")
    data object Sync : Screen("sync")
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL。注意 `Reader.createRoute(bookId)` 无参数调用因默认值仍可编译。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/navigation/Screen.kt
git commit -m "feat: add Collection route and extend Reader route with position params"
```

---

### 任务 2：BookmarkService 添加通用删除方法

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/service/BookmarkService.kt`

当前 `BookmarkService` 只有 `deleteBookmarkForPosition(bookId, chapterIndex)`（按位置查找后删除），缺少直接接受 Bookmark 对象的删除方法。`NoteService` 已有 `deleteNote(Note)` 方法，需要为 BookmarkService 补齐。

- [ ] **步骤 1：添加 deleteBookmark 方法**

在 `BookmarkService.kt` 的 `deleteBookmarkForPosition` 方法之后添加：

```kotlin
suspend fun deleteBookmark(bookmark: Bookmark) {
    bookmarkRepo.deleteBookmark(bookmark)
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/service/BookmarkService.kt
git commit -m "feat: add deleteBookmark(Bookmark) to BookmarkService"
```

---

### 任务 3：创建 CollectionViewModel

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/ui/collection/CollectionViewModel.kt`

- [ ] **步骤 1：创建目录并编写 CollectionViewModel**

```kotlin
package com.ebookreader.simplebook.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.BookmarkService
import com.ebookreader.simplebook.domain.service.NoteService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupedItems<T>(
    val book: Book,
    val items: List<T>
)

@HiltViewModel
class CollectionViewModel @Inject constructor(
    private val bookmarkService: BookmarkService,
    private val noteService: NoteService,
    private val bookService: BookService
) : ViewModel() {

    val bookmarkGroups: StateFlow<List<GroupedItems<Bookmark>>> =
        combine(
            bookmarkService.getAllBookmarks(),
            bookService.getAllBooks()
        ) { bookmarks, books ->
            val bookMap = books.associateBy { it.id }
            bookmarks
                .groupBy { it.bookId }
                .mapNotNull { (bookId, bms) ->
                    bookMap[bookId]?.let { book -> GroupedItems(book, bms) }
                }
                .sortedByDescending { it.items.maxOfOrNull { it.createdAt } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val noteGroups: StateFlow<List<GroupedItems<Note>>> =
        combine(
            noteService.getAllNotes(),
            bookService.getAllBooks()
        ) { notes, books ->
            val bookMap = books.associateBy { it.id }
            notes
                .groupBy { it.bookId }
                .mapNotNull { (bookId, ns) ->
                    bookMap[bookId]?.let { book -> GroupedItems(book, ns) }
                }
                .sortedByDescending { it.items.maxOfOrNull { it.createdAt } }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun deleteBookmark(bookmark: Bookmark) {
        viewModelScope.launch { bookmarkService.deleteBookmark(bookmark) }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch { noteService.deleteNote(note) }
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
mkdir -p app/src/main/java/com/ebookreader/simplebook/ui/collection
git add app/src/main/java/com/ebookreader/simplebook/ui/collection/CollectionViewModel.kt
git commit -m "feat: add CollectionViewModel with grouped bookmarks/notes"
```

---

### 任务 4：创建收藏页 UI 组件

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/ui/collection/CollectionScreen.kt`

这是最大的任务。文件包含：共用组件（分组列表项、滑动删除、空状态）+ 手机端布局 + Pad 端布局 + 入口 composable。

- [ ] **步骤 1：创建 CollectionScreen.kt**

```kotlin
package com.ebookreader.simplebook.ui.collection

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
                                    bookmark.bookId,
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
                                    note.bookId,
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
            // Left: Bookmarks
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
                                bookmark.bookId,
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
            // Right: Notes
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
                                note.bookId,
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
    val expandedState = remember { mutableStateMapOf<Long, Boolean>() }
    groups.forEach { it.book.id.let { id -> if (id !in expandedState) expandedState[id] = true } }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groups.forEach { group ->
            val isExpanded = expandedState[group.book.id] ?: true
            item(key = "header_${group.book.id}") {
                GroupHeader(
                    title = group.book.title,
                    count = group.items.size,
                    isExpanded = isExpanded,
                    onClick = { expandedState[group.book.id] = !isExpanded }
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
    val expandedState = remember { mutableStateMapOf<Long, Boolean>() }
    groups.forEach { it.book.id.let { id -> if (id !in expandedState) expandedState[id] = true } }

    LazyColumn(
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        groups.forEach { group ->
            val isExpanded = expandedState[group.book.id] ?: true
            item(key = "header_${group.book.id}") {
                GroupHeader(
                    title = group.book.title,
                    count = group.items.size,
                    isExpanded = isExpanded,
                    onClick = { expandedState[group.book.id] = !isExpanded }
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
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Bookmark,
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
        confirmValue = { dismissValue ->
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
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "刚刚"
        diff < 3_600_000L -> "${diff / 60_000L}分钟前"
        diff < 86_400_000L -> "${diff / 3_600_000L}小时前"
        diff < 604_800_000L -> "${diff / 86_400_000L}天前"
        else -> "${diff / 604_800_000L}周前"
    }
}

// endregion
```

注意：需要补充 `import androidx.compose.foundation.layout.Box`。

- [ ] **步骤 2：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL。如有 import 缺失，根据编译错误补充。

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/collection/CollectionScreen.kt
git commit -m "feat: add CollectionScreen with phone/tablet adaptive layout"
```

---

### 任务 5：注册收藏页路由并更新底部导航

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/components/AdaptiveScaffold.kt`

- [ ] **步骤 1：修改 SimpleBookNavHost.kt**

在 import 区域添加：
```kotlin
import com.ebookreader.simplebook.ui.collection.CollectionScreen
```

在 `NavHost` 的 `composable(Screen.BookList.route)` 之前，添加 collection composable：
```kotlin
composable(Screen.Collection.route) {
    CollectionScreen(
        navController = navController,
        windowWidthSizeClass = windowSizeClass.widthSizeClass
    )
}
```

同时修改 Reader 的 composable，添加 charOffset 和 chapterIndex 参数：

```kotlin
composable(
    route = Screen.Reader.route,
    arguments = listOf(
        navArgument("bookId") { type = NavType.LongType },
        navArgument("charOffset") {
            type = NavType.LongType
            defaultValue = 0L
        },
        navArgument("chapterIndex") {
            type = NavType.IntType
            defaultValue = 0
        }
    )
) { backStackEntry ->
    val bookId = backStackEntry.arguments?.getLong("bookId") ?: 0L
    ReaderScreen(
        bookId = bookId,
        navController = navController,
        windowWidthSizeClass = windowSizeClass.widthSizeClass
    )
}
```

- [ ] **步骤 2：修改 AdaptiveScaffold.kt**

在 `rememberNavItems` 函数中，将收藏按钮的路由从 `Screen.BookList.route` 改为 `Screen.Collection.route`：

```kotlin
@Composable
private fun rememberNavItems(strings: AppStrings): List<NavItem> {
    return listOf(
        NavItem(Screen.BookList.route, Icons.Default.Book, strings.navBooks),
        NavItem(Screen.Collection.route, Icons.Default.Favorite, strings.navFavorites),
        NavItem(Screen.Settings.route, Icons.Default.Settings, strings.navSettings)
    )
}
```

同时在 `showNav` 判断中添加 Collection 路由（Collection 页也显示底部导航栏）：
```kotlin
val showNav = currentRoute != Screen.Reader.route
```
这行无需改动，因为 Collection 路由不是 Reader 路由，默认 showNav 为 true。

- [ ] **步骤 3：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt \
        app/src/main/java/com/ebookreader/simplebook/ui/components/AdaptiveScaffold.kt
git commit -m "feat: wire Collection route into NavHost and bottom navigation"
```

---

### 任务 6：ReaderViewModel 支持位置参数

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt`

- [ ] **步骤 1：读取位置参数并覆盖初始章节**

在 `ReaderViewModel` 中，找到 `private val bookId` 声明行，在其后添加：

```kotlin
private val initialChapterIndex: Int = savedStateHandle["chapterIndex"] ?: -1
private val initialCharOffset: Long = savedStateHandle["charOffset"] ?: 0L
```

在 `loadBook()` 方法中，找到 `readingService.loadProgress(bookId)?.let` 块的结束位置（在 `_scrollPercentage` 赋值之后、`_isLoading.value = false` 之前），添加：

```kotlin
// Override with position from Collection navigation
if (initialChapterIndex >= 0) {
    _currentChapterIndex.value = initialChapterIndex
    _scrollPercentage.value = 0f
}
```

完整的 `loadBook()` 方法中，修改后的关键片段应该是：

```kotlin
readingService.loadProgress(bookId)?.let { progress ->
    _currentChapterIndex.value = progress.chapterIndex
    val totalChapters = _chapters.value.size
    _scrollPercentage.value = if (totalChapters > 0) {
        ((progress.percentage * totalChapters) - progress.chapterIndex).toFloat().coerceIn(0f, 1f)
    } else 0f
}

// Override with position from Collection navigation
if (initialChapterIndex >= 0) {
    _currentChapterIndex.value = initialChapterIndex
    _scrollPercentage.value = 0f
}

_isLoading.value = false
refreshBookmarkStatus()
```

注意：`initialChapterIndex` 默认值 -1，当从现有导航（如书架点击）进入阅读器时，SavedStateHandle 中的 `chapterIndex` 会使用 NavArgument 的 defaultValue（0），此时 `0 >= 0` 为 true。为区分"用户没传参数"和"用户传了章节 0"，将 NavArgument 的 defaultValue 改为 -1。

回到 `Screen.kt` 和 `SimpleBookNavHost.kt`，将 chapterIndex 的默认值改为 -1：

`Screen.kt`：
```kotlin
data object Reader : Screen("reader/{bookId}?charOffset={charOffset}&chapterIndex={chapterIndex}") {
    fun createRoute(bookId: Long, charOffset: Long = 0L, chapterIndex: Int = -1) =
        "reader/$bookId?charOffset=$charOffset&chapterIndex=$chapterIndex"
}
```

`SimpleBookNavHost.kt` 中 Reader 的 navArgument：
```kotlin
navArgument("chapterIndex") {
    type = NavType.IntType
    defaultValue = -1
}
```

- [ ] **步骤 2：构建验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/reader/ReaderViewModel.kt \
        app/src/main/java/com/ebookreader/simplebook/ui/navigation/Screen.kt \
        app/src/main/java/com/ebookreader/simplebook/ui/navigation/SimpleBookNavHost.kt
git commit -m "feat: ReaderViewModel accepts initial position from Collection navigation"
```

---

### 任务 7：构建验证与冒烟测试

- [ ] **步骤 1：完整构建**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL，无 warning

- [ ] **步骤 2：安装到设备并验证**

运行：`./gradlew installDebug`

在设备/模拟器上验证：

| 验证项 | 操作 | 预期 |
|--------|------|------|
| 收藏按钮 | 点击底部导航"收藏" | 显示收藏页，含书签/笔记两个 Tab |
| 空状态 | 无书签时查看书签 Tab | 显示"还没有书签" |
| 书签列表 | 有书签时查看书签 Tab | 按书分组显示，每组显示书名+数量 |
| 笔记列表 | 有笔记时查看笔记 Tab | 按书分组显示，预览前 80 字 |
| 折叠/展开 | 点击书名行 | 该组折叠/展开 |
| 点击跳转 | 点击某条书签 | 打开阅读器，跳转到对应章节 |
| 滑动删除 | 向左滑某条书签/笔记 | 出现红色删除图标，松手后删除 |
| Pad 布局 | 在 Pad/宽屏设备上查看 | 书签笔记左右并排，无 Tab |
| 底部导航 | 在阅读器按返回 | 回到收藏页 |
| Google Drive 同步 | 删除书签后触发同步 | 变更同步到云端 |
