package com.ebookreader.simplebook.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.AppVersion
import com.ebookreader.simplebook.domain.model.AppStrings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.platform.ReauthRequest
// TODO: Platform-specific — rememberLauncherForActivityResult is Android-only
// import androidx.activity.compose.rememberLauncherForActivityResult
// import androidx.activity.result.contract.ActivityResultContracts
import com.ebookreader.simplebook.domain.service.ImportStatus
import com.ebookreader.simplebook.domain.service.SyncStatus
import com.ebookreader.simplebook.ui.sync.SyncTimeLabel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import com.ebookreader.simplebook.ui.theme.LocalRippleColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    navController: NavController? = null,
    onSignInClick: (() -> Unit)? = null,
    onReauthClick: ((ReauthRequest) -> Unit)? = null,
    viewModel: SettingsViewModel = koinViewModel(),
    syncViewModel: SyncViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }
    val syncStatus by syncViewModel.syncStatus.collectAsState()
    // TODO: Platform-specific — auth requires AuthManager
    // val account by viewModel.authManager.signedInAccount.collectAsState()
    val isSignedIn by syncViewModel.isSignedIn.collectAsState()
    val accountEmail by syncViewModel.accountEmail.collectAsState()
    val signInError by syncViewModel.signInError.collectAsState()
    val lastSyncedAt by syncViewModel.lastSyncedAt.collectAsState()
    val autoSyncEnabled by syncViewModel.autoSyncEnabled.collectAsState()
    val cleanDriveState by viewModel.cleanDriveState.collectAsState()
    val importStatus by viewModel.importStatus.collectAsState()

    val reauthRequest by syncViewModel.reauthRequest.collectAsState()

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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            reauthRequest?.let { req ->
                AlertDialog(
                    onDismissRequest = { syncViewModel.consumeReauthRequest() },
                    title = { Text("需要重新授权") },
                    text = { Text("Google 授权已失效，请重新登录后重试同步。") },
                    confirmButton = {
                        TextButton(onClick = {
                            syncViewModel.consumeReauthRequest()
                            if (onReauthClick != null) onReauthClick.invoke(req)
                            else onSignInClick?.invoke()
                        }) { Text("重新登录") }
                    },
                    dismissButton = {
                        TextButton(onClick = { syncViewModel.consumeReauthRequest() }) {
                            Text(strings.cancel)
                        }
                    }
                )
            }

            // ── 字体大小 ──
            SectionHeader(strings.fontSize)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = settings.fontSize,
                    onValueChange = { viewModel.updateFontSize(it) },
                    valueRange = 12f..28f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "${settings.fontSize.toInt()} sp",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ── 行高 ──
            SectionHeader(strings.lineHeight)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Slider(
                    value = settings.lineHeight,
                    onValueChange = { viewModel.updateLineHeight(it) },
                    valueRange = 1.0f..2.5f,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    String.format("%.1fx", settings.lineHeight),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // ── 主题设置 ──
            SectionHeader(strings.themeSetting)

            // 明亮主题
            SubLabel(strings.lightTheme)
            val lightThemes = listOf(
                ReaderTheme.DEFAULT_WHITE to strings.defaultWhite,
                ReaderTheme.SEPIA to strings.sepia,
                ReaderTheme.CHERRY_PINK to strings.cherryPink,
                ReaderTheme.MINT_GREEN to strings.mintGreen,
                ReaderTheme.SAPPHIRE_BLUE to strings.sapphireBlue,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                lightThemes.forEach { (theme, name) ->
                    ThemeCard(
                        theme = theme,
                        name = name,
                        isSelected = settings.theme == theme,
                        onClick = { viewModel.updateTheme(theme) }
                    )
                }
            }

            // 夜间模式
            SubLabel(strings.nightMode)
            val darkThemes = listOf(
                ReaderTheme.NIGHT_SAKURA to strings.nightSakura,
                ReaderTheme.DARK_GREEN to strings.darkGreen,
                ReaderTheme.DEEP_SEA to strings.deepSea,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                darkThemes.forEach { (theme, name) ->
                    ThemeCard(
                        theme = theme,
                        name = name,
                        isSelected = settings.theme == theme,
                        onClick = { viewModel.updateTheme(theme) }
                    )
                }
            }

            // ── Google Drive 同步 ──
            HorizontalDivider()
            SectionHeader(strings.syncTitle)
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
                )

                val isSyncing = syncStatus is SyncStatus.Syncing
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    OutlinedButton(
                        onClick = { syncViewModel.syncNow() },
                        enabled = !isSyncing
                    ) {
                        Text(if (isSyncing) "..." else strings.syncNow)
                    }
                    OutlinedButton(onClick = { syncViewModel.signOut() }) {
                        Text(strings.syncSignOut)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { onSignInClick?.invoke() },
                    modifier = Modifier.padding(top = 4.dp)
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

            // ── 同步设置（始终显示：未登录时切换无害，登录后再生效） ──
            HorizontalDivider()
            SectionHeader(strings.syncSettingsTitle)
            Text(
                strings.syncSettingsDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(strings.autoSyncLabel, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoSyncEnabled,
                    onCheckedChange = { syncViewModel.toggleAutoSync() }
                )
            }

            // ── 从 Drive 导入 ──
            HorizontalDivider()
            SectionHeader(strings.driveImportTitle)
            Text(
                strings.driveImportDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSignedIn) {
                when (val status = importStatus) {
                    is ImportStatus.Idle -> {
                        OutlinedButton(
                            onClick = { viewModel.importFromDrive() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(strings.driveImportButton)
                        }
                    }
                    is ImportStatus.Importing -> {
                        Text(
                            strings.driveImporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is ImportStatus.Success -> {
                        val msg = if (status.count == 0) strings.driveImportEmpty
                                  else strings.driveImportResult(status.count)
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.importFromDrive() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(strings.driveImportButton)
                        }
                    }
                    is ImportStatus.Error -> {
                        Text(
                            "导入失败: ${status.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.importFromDrive() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(strings.driveImportButton)
                        }
                    }
                }
            }

            // ── 网盘清理 ──
            HorizontalDivider()
            SectionHeader(strings.cleanDriveTitle)
            Text(
                strings.cleanDriveDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSignedIn) {
                when (cleanDriveState.phase) {
                    CleanDrivePhase.IDLE -> {
                        OutlinedButton(
                            onClick = { viewModel.startCleanScan() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(strings.cleanDriveButton)
                        }
                    }
                    CleanDrivePhase.SCANNING -> {
                        Text(
                            strings.cleanDriveScanning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    CleanDrivePhase.FOUND -> {
                        val sizeStr = remember(cleanDriveState.deletedBookSize) {
                            when {
                                cleanDriveState.deletedBookSize >= 1_000_000_000 ->
                                    "%.1f GB".format(cleanDriveState.deletedBookSize / 1_000_000_000.0)
                                cleanDriveState.deletedBookSize >= 1_000_000 ->
                                    "%.1f MB".format(cleanDriveState.deletedBookSize / 1_000_000.0)
                                cleanDriveState.deletedBookSize >= 1_000 ->
                                    "%.0f KB".format(cleanDriveState.deletedBookSize / 1_000.0)
                                else -> "${cleanDriveState.deletedBookSize} B"
                            }
                        }
                        Text(
                            strings.cleanDriveFound(cleanDriveState.deletedBookCount, sizeStr),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Button(onClick = { viewModel.confirmClean() }) {
                                Text(strings.cleanDriveConfirm)
                            }
                            OutlinedButton(onClick = { viewModel.cancelClean() }) {
                                Text(strings.cancel)
                            }
                        }
                    }
                    CleanDrivePhase.CLEANING -> {
                        Text(
                            strings.cleanDriveCleaning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    CleanDrivePhase.DONE -> {
                        val result = cleanDriveState.result
                        if (result == "empty") {
                            Text(
                                strings.cleanDriveNoDeleted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else if (result != null && result.startsWith("error|")) {
                            Text(
                                result.removePrefix("error|"),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else if (result != null) {
                            val parts = result.split("|")
                            val count = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0
                            val size = parts.getOrElse(1) { "0 B" }
                            Text(
                                strings.cleanDriveResult(count, size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.dismissCleanResult() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(strings.cleanDriveDone)
                        }
                    }
                }
            }

            // ── 关于 ──
            HorizontalDivider()
            SectionHeader(strings.about)
            val versionName = AppVersion.NAME
            // Update state handling
            val updateState by viewModel.updateState.collectAsState()

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "SimpleBook v${versionName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { viewModel.checkForUpdate() }) {
                    Text("检查更新")
                }
                when {
                    updateState.checking -> {
                        Spacer(Modifier.width(8.dp))
                        Text("正在检查...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    updateState.error != null -> {
                        Spacer(Modifier.width(8.dp))
                        Text(updateState.error!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // New version dialog
            if (updateState.latestVersion != null && updateState.downloadUrl != null) {
                val latestVer = updateState.latestVersion!!
                val dlUrl = updateState.downloadUrl!!
                Dialog(onDismissRequest = { viewModel.dismissUpdate() }) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 70.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 80.dp, bottom = 16.dp)
                            ) {
                                Text(
                                    "发现新版本 $latestVer",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Spacer(Modifier.height(8.dp))

                                Text(
                                    "当前版本 v${versionName}，是否下载更新？",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(Modifier.height(20.dp))

                                Row(
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                ) {
                                    TextButton(onClick = { viewModel.dismissUpdate() }) {
                                        Text(strings.cancel)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    // TODO: Platform-specific — download requires Android DownloadManager
                                    // Button(onClick = { viewModel.downloadUpdate(dlUrl, latestVer) }) {
                                    //     Text("下载")
                                    // }
                                    OutlinedButton(onClick = {
                                        // Open in browser as fallback
                                        println("Download URL: $dlUrl")
                                    }) {
                                        Text("下载")
                                    }
                                }
                            }
                        }

                        // TODO: Replace with CMP resource image once migrated
                        Text(
                            text = "SimpleBook",
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun SubLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Medium
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, top = 2.dp)
    )
}

@Composable
private fun ThemeCard(
    theme: ReaderTheme,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = Color(theme.backgroundColor)
    val accentColor = Color(theme.accentColor)
    val textColor = Color(theme.textColor)
    val rippleColor = LocalRippleColor.current

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) accentColor else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(color = rippleColor, bounded = true),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Accent bar at bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(accentColor)
            )
            // Text preview lines
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp, end = 10.dp)
                    .fillMaxWidth(0.7f)
                    .height(2.dp)
                    .background(textColor.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp, top = 14.dp, end = 10.dp)
                    .fillMaxWidth(0.5f)
                    .height(2.dp)
                    .background(textColor.copy(alpha = 0.2f))
            )
            // Check icon
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .background(accentColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = Color.White
                    )
                }
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                fontSize = 11.sp
            ),
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
