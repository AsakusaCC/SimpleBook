package com.ebookreader.simplebook.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.ebookreader.simplebook.R
import com.ebookreader.simplebook.domain.model.AppStrings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.model.getStrings
import com.ebookreader.simplebook.domain.service.SyncStatus
import com.ebookreader.simplebook.ui.sync.SyncTimeLabel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import com.ebookreader.simplebook.ui.theme.LocalRippleColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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

            // ── 关于 ──
            SectionHeader(strings.about)
            val context = LocalContext.current
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrDefault("0.8")
            }
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
                AlertDialog(
                        onDismissRequest = { viewModel.dismissUpdate() },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Image(
                                    painter = painterResource(R.drawable.mizuki_logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp)
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "发现新版本 $latestVer",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "当前版本 v${versionName}，是否下载更新？",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.downloadUpdate(dlUrl, latestVer)
                            }) { Text("下载") }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissUpdate() }) { Text(strings.cancel) }
                        }
                    )
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
