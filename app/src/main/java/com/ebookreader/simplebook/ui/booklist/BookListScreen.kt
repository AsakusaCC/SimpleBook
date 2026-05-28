package com.ebookreader.simplebook.ui.booklist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.SortOrder
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.domain.service.SyncStatus
import com.ebookreader.simplebook.ui.components.AdaptiveBookGrid
import com.ebookreader.simplebook.ui.components.SpeedDialFAB
import com.ebookreader.simplebook.ui.components.SpeedDialItem
import com.ebookreader.simplebook.ui.sync.SyncStatusIcon
import com.ebookreader.simplebook.ui.sync.SyncTimeLabel
import com.ebookreader.simplebook.ui.sync.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    onBookClick: (Book) -> Unit,
    onSyncClick: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: BookListViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val currentItems by viewModel.currentItems.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val isSpeedDialExpanded by viewModel.isSpeedDialExpanded.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }

    var showLayoutMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var longPressedBook by remember { mutableStateOf<Book?>(null) }
    var folderToDelete by remember { mutableStateOf<com.ebookreader.simplebook.domain.model.Folder?>(null) }

    val syncStatus by syncViewModel.syncStatus.collectAsState()
    val isSignedIn = syncViewModel.isSignedIn
    val isSyncing = syncStatus is SyncStatus.Syncing
    val lastSyncedAt by syncViewModel.lastSyncedAt.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromUris(uris)
        }
        viewModel.dismissSpeedDial()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (currentFolderId != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.exitFolder() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                            }
                            Text(currentFolderName ?: "")
                        }
                    } else {
                        Box {
                            Row(
                                modifier = Modifier.clickable { showLayoutMenu = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.navBooks)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showLayoutMenu,
                                onDismissRequest = { showLayoutMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.layoutLargeGrid) },
                                    onClick = {
                                        viewModel.updateLayoutMode(LayoutMode.LARGE_GRID)
                                        showLayoutMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.GridView, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.layoutSmallGrid) },
                                    onClick = {
                                        viewModel.updateLayoutMode(LayoutMode.SMALL_GRID)
                                        showLayoutMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ViewModule, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.layoutList) },
                                    onClick = {
                                        viewModel.updateLayoutMode(LayoutMode.LIST)
                                        showLayoutMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (isSignedIn) {
                        SyncTimeLabel(
                            lastSyncedAt = lastSyncedAt,
                            isSyncing = isSyncing
                        )
                    }
                    SyncStatusIcon(
                        isSyncing = isSyncing,
                        isSignedIn = isSignedIn,
                        onClick = {
                            if (isSignedIn) {
                                syncViewModel.syncNow()
                                onSyncClick?.invoke()
                            } else {
                                onNavigateToSettings?.invoke()
                            }
                        }
                    )
                }
            )
        },
        floatingActionButton = {
            SpeedDialFAB(
                items = listOf(
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = strings.importBooks,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            importLauncher.launch(arrayOf("application/epub+zip", "text/plain"))
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        label = strings.newFolder,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            showNewFolderDialog = true
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        label = strings.sort,
                        onClick = { showSortMenu = true }
                    )
                ),
                isExpanded = isSpeedDialExpanded,
                onToggle = { viewModel.toggleSpeedDial() }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentItems.isEmpty()) {
                Text(
                    text = strings.noContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AdaptiveBookGrid(
                    items = currentItems,
                    layoutMode = settings.layoutMode,
                    windowWidthSizeClass = windowWidthSizeClass,
                    onBookClick = onBookClick,
                    onBookLongClick = { book -> longPressedBook = book },
                    onFolderClick = { folder -> viewModel.enterFolder(folder.uuid, folder.name) },
                    onFolderLongClick = { folder -> folderToDelete = folder },
                    unknownAuthorText = strings.unknownAuthor,
                    bookCountText = { count -> strings.bookCount(count) }
                )
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = {
                    showSortMenu = false
                    viewModel.dismissSpeedDial()
                }
            ) {
                DropdownMenuItem(
                    text = { Text(strings.sortByLastRead) },
                    onClick = {
                        viewModel.updateSortOrder(SortOrder.LAST_READ)
                        showSortMenu = false
                        viewModel.dismissSpeedDial()
                    }
                )
                DropdownMenuItem(
                    text = { Text(strings.sortByName) },
                    onClick = {
                        viewModel.updateSortOrder(SortOrder.NAME)
                        showSortMenu = false
                        viewModel.dismissSpeedDial()
                    }
                )
            }
        }
    }

    // Move to folder dialog
    longPressedBook?.let { book ->
        AlertDialog(
            onDismissRequest = { longPressedBook = null },
            title = { Text(strings.moveToFolder) },
            text = {
                val folders by viewModel.allFoldersForDialog.collectAsState()
                if (folders.isEmpty()) {
                    Text(strings.noFolders)
                } else {
                    LazyColumn {
                        if (currentFolderId != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.moveBookToFolder(book.uuid, null)
                                            longPressedBook = null
                                        }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(strings.moveBackToShelf)
                                }
                            }
                        }
                        items(folders) { (folder, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.moveBookToFolder(book.uuid, folder.uuid)
                                        longPressedBook = null
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(folder.name)
                                    Text(
                                        text = strings.bookCount(count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressedBook = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // New folder dialog
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewFolderDialog = false
                newFolderName = ""
            },
            title = { Text(strings.createFolder) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(strings.folderName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim())
                            newFolderName = ""
                        }
                        showNewFolderDialog = false
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    newFolderName = ""
                }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // Delete folder dialog
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(strings.deleteBook) },
            text = { Text("确认删除文件夹「${folder.name}」吗？文件夹内的书籍将移回主书架。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFolder(folder.uuid)
                        folderToDelete = null
                    }
                ) {
                    Text(strings.delete)
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
