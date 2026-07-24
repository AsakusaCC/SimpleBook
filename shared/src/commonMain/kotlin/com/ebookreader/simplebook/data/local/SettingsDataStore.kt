package com.ebookreader.simplebook.data.local

import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

expect class SettingsDataStore {
    val settings: Flow<ReaderSettings>
    suspend fun updateFontSize(size: Float)
    suspend fun updateLineHeight(height: Float)
    suspend fun updateTheme(theme: ReaderTheme)
    suspend fun updateLanguage(language: String)
    suspend fun updateLayoutMode(layoutMode: LayoutMode)
    suspend fun updateSortOrder(sortOrder: SortOrder)
}
