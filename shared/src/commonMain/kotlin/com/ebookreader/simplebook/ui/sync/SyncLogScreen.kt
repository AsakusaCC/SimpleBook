package com.ebookreader.simplebook.ui.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = koinViewModel()
) {
    val syncLogs by viewModel.syncLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (syncLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("暂无同步记录", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp)
            ) {
                items(syncLogs, key = { it.id }) { log ->
                    SyncLogItem(log)
                }
            }
        }
    }
}

@Composable
private fun SyncLogItem(log: SyncLogEntity) {
    val typeLabel = when (log.entityType) {
        "progress" -> "阅读进度"
        "bookmark" -> "书签"
        "highlight" -> "高亮"
        "note" -> "笔记"
        else -> log.entityType
    }
    val actionLabel = when (log.action) {
        "remote_won" -> "远端覆盖"
        "local_won" -> "保留本地"
        else -> log.action
    }
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(log.resolvedAt))

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$typeLabel · $actionLabel", style = MaterialTheme.typography.titleSmall)
        Text(time, style = MaterialTheme.typography.bodySmall)
    }
}
