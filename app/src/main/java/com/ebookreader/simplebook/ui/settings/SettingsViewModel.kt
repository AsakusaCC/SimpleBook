package com.ebookreader.simplebook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
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

    fun updateFontSize(size: Float) {
        viewModelScope.launch { settingsDataStore.updateFontSize(size) }
    }

    fun updateLineHeight(height: Float) {
        viewModelScope.launch { settingsDataStore.updateLineHeight(height) }
    }

    fun updateBackgroundColor(color: Long) {
        viewModelScope.launch {
            settingsDataStore.updateBackgroundColor(color)
            // Auto-switch text color to match background brightness
            val textColor = when (color) {
                0xFF2B2B2BL, 0xFF000000L -> 0xFFFFFFFFL // Dark/Black → white text
                else -> 0xFF000000L // White/Sepia → black text
            }
            settingsDataStore.updateTextColor(textColor)
        }
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
