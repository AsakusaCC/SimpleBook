package com.ebookreader.simplebook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.service.ImportStatus
// TODO: Platform-specific — SyncService has Android dependencies
// import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
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

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    // TODO: Platform-specific — SyncService + AuthManager deferred to platform-specific DI
    // private val syncService: SyncService,
    // val authManager: AuthManager
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    // TODO: Platform-specific — sync status requires SyncService
    val syncStatus: StateFlow<SyncStatus> = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    // TODO: Platform-specific — auth requires AuthManager
    val isSignedIn: Boolean = false
    val accountEmail: String? = null
    val signInError: StateFlow<String?> = MutableStateFlow(null)

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val _cleanDriveState = MutableStateFlow(CleanDriveState())
    val cleanDriveState: StateFlow<CleanDriveState> = _cleanDriveState.asStateFlow()

    // TODO: Platform-specific — import status requires SyncService
    val importStatus: StateFlow<ImportStatus> = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportStatus.Idle)

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

    // TODO: Platform-specific — sync requires SyncService
    fun syncNow() {
        // syncService.syncAll()
    }

    // TODO: Platform-specific — sign out requires AuthManager
    fun signOut() {
        // authManager.signOut()
    }

    // TODO: Platform-specific — clean drive requires SyncService
    fun startCleanScan() {
        // Deferred to platform-specific implementation
    }

    fun confirmClean() {
        // Deferred to platform-specific implementation
    }

    fun cancelClean() {
        _cleanDriveState.value = CleanDriveState(phase = CleanDrivePhase.IDLE)
    }

    fun dismissCleanResult() {
        _cleanDriveState.value = CleanDriveState(phase = CleanDrivePhase.IDLE)
    }

    // TODO: Platform-specific — import from drive requires SyncService
    fun importFromDrive() {
        // Deferred to platform-specific implementation
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
