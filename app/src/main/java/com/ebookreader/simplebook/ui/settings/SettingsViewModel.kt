package com.ebookreader.simplebook.ui.settings

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.net.URL
import javax.inject.Inject

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

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val application: Application,
    private val settingsDataStore: SettingsDataStore,
    private val syncService: SyncService,
    val authManager: AuthManager
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    val isSignedIn: Boolean get() = authManager.isSignedIn

    val accountEmail: String? get() = authManager.signedInAccount.value?.email

    val signInError: StateFlow<String?> = authManager.signInError

    private val _updateState = MutableStateFlow(UpdateState())
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private val currentVersionName: String by lazy {
        runCatching {
            application.packageManager.getPackageInfo(application.packageName, 0).versionName
                ?: "0.8"
        }.getOrDefault("0.8")
    }

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

    fun downloadUpdate(url: String, version: String) {
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("SimpleBook $version")
            .setDescription("正在下载新版本")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "SimpleBook-$version.apk")

        (application.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
        _updateState.value = UpdateState()
    }

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

    fun syncNow() {
        viewModelScope.launch { syncService.syncAll() }
    }

    fun signOut() {
        authManager.signOut()
    }
}
