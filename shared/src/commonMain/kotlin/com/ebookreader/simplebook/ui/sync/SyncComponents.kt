package com.ebookreader.simplebook.ui.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncStatusIcon(
    isSyncing: Boolean,
    isSignedIn: Boolean,
    lastSyncedAt: Long? = null,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = when {
                    isSyncing -> Icons.Default.CloudSync
                    isSignedIn -> Icons.Default.CloudDone
                    else -> Icons.Default.CloudOff
                },
                contentDescription = "Sync",
                tint = when {
                    isSyncing -> MaterialTheme.colorScheme.primary
                    isSignedIn -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
fun SyncTimeLabel(
    lastSyncedAt: Long?,
    isSyncing: Boolean,
    modifier: Modifier = Modifier
) {
    val timeText = when {
        isSyncing -> "同步中..."
        lastSyncedAt != null -> {
            val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
            "上次同步 ${sdf.format(Date(lastSyncedAt))}"
        }
        else -> null
    }
    if (timeText != null) {
        Text(
            text = timeText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier
        )
    }
}
