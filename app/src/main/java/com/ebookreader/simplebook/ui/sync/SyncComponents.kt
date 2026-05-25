package com.ebookreader.simplebook.ui.sync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun SyncStatusIcon(
    isSyncing: Boolean,
    isSignedIn: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
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
