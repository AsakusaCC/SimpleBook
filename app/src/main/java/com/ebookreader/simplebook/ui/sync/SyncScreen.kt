package com.ebookreader.simplebook.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ebookreader.simplebook.data.local.entity.ConflictRecordEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val conflictCount by viewModel.conflictCount.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步冲突解决") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        bottomBar = {
            if (conflictCount > 0) {
                BottomAppBar {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(onClick = { viewModel.resolveAllConflicts(false) }) {
                            Text("全部保留本地")
                        }
                        Button(onClick = { viewModel.resolveAllConflicts(true) }) {
                            Text("全部使用远端")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (conflictCount == 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("没有冲突", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .padding(16.dp)
            ) {
                item {
                    Text(
                        "有 $conflictCount 条数据冲突需要解决",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(16.dp))
                }
                items(conflicts, key = { it.id }) { conflict ->
                    ConflictItem(
                        conflict = conflict,
                        onKeepLocal = { viewModel.resolveConflict(conflict.id, false) },
                        onUseRemote = { viewModel.resolveConflict(conflict.id, true) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictItem(
    conflict: ConflictRecordEntity,
    onKeepLocal: () -> Unit,
    onUseRemote: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val typeLabel = when (conflict.entityType) {
                    "progress" -> "阅读进度"
                    "bookmark" -> "书签"
                    "highlight" -> "高亮"
                    "note" -> "笔记"
                    else -> conflict.entityType
                }
                Text(typeLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    "本地版本: ${conflict.localSyncVersion}  |  远端版本: ${conflict.remoteSyncVersion}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                TextButton(onClick = onKeepLocal) {
                    Text("保留本地")
                }
                TextButton(onClick = onUseRemote) {
                    Text("使用远端")
                }
            }
        }
    }
}
