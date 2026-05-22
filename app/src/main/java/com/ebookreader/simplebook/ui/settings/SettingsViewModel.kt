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
        viewModelScope.launch { settingsDataStore.updateBackgroundColor(color) }
    }
}
