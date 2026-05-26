package com.ebookreader.simplebook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.domain.service.SyncStatus
import com.ebookreader.simplebook.ui.sync.SyncTimeLabel
import com.ebookreader.simplebook.ui.sync.SyncViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onSignInClick: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }
    val syncStatus by viewModel.syncStatus.collectAsState()
    val account by viewModel.authManager.signedInAccount.collectAsState()
    val isSignedIn = account != null
    val accountEmail = account?.email
    val signInError by viewModel.signInError.collectAsState()
    val lastSyncedAt by syncViewModel.lastSyncedAt.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.settings) },
                navigationIcon = {
                    if (navController != null && navController.previousBackStackEntry != null) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Font Size
            Text(strings.fontSize, style = MaterialTheme.typography.titleMedium)
            Text("${settings.fontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.fontSize,
                onValueChange = { viewModel.updateFontSize(it) },
                valueRange = 12f..28f,
                modifier = Modifier.fillMaxWidth()
            )

            // Line Height
            Text(strings.lineHeight, style = MaterialTheme.typography.titleMedium)
            Text(String.format("%.1fx", settings.lineHeight), style = MaterialTheme.typography.bodyMedium)
            Slider(
                value = settings.lineHeight,
                onValueChange = { viewModel.updateLineHeight(it) },
                valueRange = 1.0f..2.5f,
                modifier = Modifier.fillMaxWidth()
            )

            // Background Color
            Text(strings.background, style = MaterialTheme.typography.titleMedium)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val colorPresets = listOf(
                    strings.white to 0xFFFFFFFF,
                    strings.sepia to 0xFFF5F0E1,
                    strings.dark to 0xFF2B2B2B,
                    strings.black to 0xFF000000,
                )
                colorPresets.forEach { (name, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(color), CircleShape)
                                .border(
                                    width = if (settings.backgroundColor == color) 3.dp else 1.dp,
                                    color = if (settings.backgroundColor == color) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.updateBackgroundColor(color) }
                        )
                        Text(name, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // About
            Text(strings.about, style = MaterialTheme.typography.titleMedium)
            Text("SimpleBook v1.0", style = MaterialTheme.typography.bodyMedium)

            // Google Drive Sync Section
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(strings.syncTitle, style = MaterialTheme.typography.titleMedium)
            Text(
                strings.syncDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSignedIn) {
                accountEmail?.let { email ->
                    Text(
                        strings.syncSignedIn.format(email),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SyncTimeLabel(
                    lastSyncedAt = lastSyncedAt,
                    isSyncing = syncStatus is SyncStatus.Syncing,
                    modifier = Modifier.padding(top = 4.dp)
                )

                val isSyncing = syncStatus is SyncStatus.Syncing
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing
                    ) {
                        Text(if (isSyncing) "..." else strings.syncNow)
                    }
                    OutlinedButton(onClick = { viewModel.signOut() }) {
                        Text(strings.syncSignOut)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onSignInClick?.invoke() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(strings.syncSignIn)
                }
                if (signInError != null) {
                    Text(
                        text = "登录失败: $signInError",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
