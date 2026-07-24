package com.ebookreader.simplebook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.service.ImportStatus
import com.ebookreader.simplebook.domain.service.SyncService
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URL

data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    val name: String,
    val assets: List<GitHubAsset>
)

data class GitHubAsset(
    val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

data class UpdateState(
    val checking: Boolean = false,
    val latestVersion: String? = null,
    val downloadUrl: String? = null,
    val error: String? = null
)

data class CleanDriveState(
    val phase: CleanDrivePhase = CleanDrivePhase.IDLE,
    val deletedBookCount: Int = 0,
    val deletedBookSize: Long = 0,
    val result: String? = null
)

enum class CleanDrivePhase {
    IDLE,           // show button
    SCANNING,       // scanning Drive...
    FOUND,          // show count + confirm/cancel
    CLEANING,       // cleaning...
    DONE            // show result
}

/**
 * 设置页 ViewModel。
 *
 * - 阅读设置（字号/行高/主题/语言）：直接读写 [SettingsDataStore]。
 * - 检查更新：查 GitHub Releases。
 * - Drive 导入 / 网盘清理：委托给 [SyncService]（KMP 迁移后 SyncService 已在 commonMain，
 *   Phase 4 期间被 stub 化，现已接回真实能力）。
 *
 * 同步状态 / 登录态 / 退出登录 / 重新授权等由 [com.ebookreader.simplebook.ui.sync.SyncViewModel]
 * 统一管理，本类不再持有（避免双真相）。
 */
class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val syncService: SyncService
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    /** Drive 导入进度，直接透传 [SyncService.importStatus]。 */
    val importStatus: StateFlow<ImportStatus> = syncService.importStatus

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _cleanDriveState = MutableStateFlow(CleanDriveState())
    val cleanDriveState: StateFlow<CleanDriveState> = _cleanDriveState.asStateFlow()

    // TODO: Desktop compatibility - version name should be injected via Koin
    private val currentVersionName: String = "0.8"

    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState(checking = true)
            try {
                val conn = URL(RELEASES_API).openConnection() as java.net.HttpURLConnection
                conn.setRequestProperty("User-Agent", "SimpleBook")
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val release = Gson().fromJson(json, GitHubRelease::class.java)
                val remoteVersion = release.tagName.removePrefix("v")
                val apkAsset = release.assets.find { it.name.endsWith(".apk") }

                if (isNewerVersion(remoteVersion, currentVersionName) && apkAsset != null) {
                    _updateState.value = UpdateState(
                        latestVersion = release.tagName,
                        downloadUrl = apkAsset.browserDownloadUrl
                    )
                } else {
                    _updateState.value = UpdateState(error = "已是最新版本")
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState(error = "检查失败: ${e.message}")
            }
        }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState()
    }

    // TODO: Platform-specific — download requires Android DownloadManager
    // fun downloadUpdate(url: String, version: String) { ... }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }

    companion object {
        private const val RELEASES_API =
            "https://api.github.com/repos/AsakusaCC/SimpleBook/releases/latest"
    }

    fun updateFontSize(size: Float) {
        viewModelScope.launch { settingsDataStore.updateFontSize(size) }
    }

    fun updateLineHeight(height: Float) {
        viewModelScope.launch { settingsDataStore.updateLineHeight(height) }
    }

    fun updateTheme(theme: ReaderTheme) {
        viewModelScope.launch { settingsDataStore.updateTheme(theme) }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch { settingsDataStore.updateLanguage(language) }
    }

    // ── Drive Import ────────────────────────────────────────────────

    /** 从 Drive 的 SimpleBook/Import/ 文件夹导入 epub/txt。 */
    fun importFromDrive() {
        viewModelScope.launch { syncService.importFromDriveFolder() }
    }

    // ── Clean deleted books from Drive ──────────────────────────────

    /** 扫描 Drive 上标记为已删除的书，进入 FOUND 等待用户确认。 */
    fun startCleanScan() {
        viewModelScope.launch {
            _cleanDriveState.value = CleanDriveState(phase = CleanDrivePhase.SCANNING)
            try {
                val deleted = syncService.scanDeletedRemoteBooks()
                if (deleted.isEmpty()) {
                    _cleanDriveState.value = CleanDriveState(phase = CleanDrivePhase.DONE, result = "empty")
                } else {
                    _cleanDriveState.value = CleanDriveState(
                        phase = CleanDrivePhase.FOUND,
                        deletedBookCount = deleted.size,
                        deletedBookSize = deleted.sumOf { it.fileSize }
                    )
                }
            } catch (e: Exception) {
                _cleanDriveState.value =
                    CleanDriveState(phase = CleanDrivePhase.DONE, result = "error|${e.message}")
            }
        }
    }

    /** 用户确认后执行清理（删除 Drive 文件夹 + 硬删本地记录）。 */
    fun confirmClean() {
        viewModelScope.launch {
            _cleanDriveState.value = _cleanDriveState.value.copy(phase = CleanDrivePhase.CLEANING)
            try {
                val result = syncService.cleanDeletedRemoteBooks()
                _cleanDriveState.value = CleanDriveState(
                    phase = CleanDrivePhase.DONE,
                    result = "${result.cleanedCount}|${formatFileSize(result.cleanedSize)}"
                )
            } catch (e: Exception) {
                _cleanDriveState.value =
                    CleanDriveState(phase = CleanDrivePhase.DONE, result = "error|${e.message}")
            }
        }
    }

    fun cancelClean() {
        _cleanDriveState.value = CleanDriveState(phase = CleanDrivePhase.IDLE)
    }

    fun dismissCleanResult() {
        _cleanDriveState.value = CleanDriveState(phase = CleanDrivePhase.IDLE)
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
            bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
            else -> "$bytes B"
        }
    }
}
