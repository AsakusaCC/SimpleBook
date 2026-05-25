package com.ebookreader.simplebook.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.domain.model.ReaderSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

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
}
