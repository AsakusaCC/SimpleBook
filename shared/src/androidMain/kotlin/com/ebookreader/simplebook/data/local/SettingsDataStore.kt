package com.ebookreader.simplebook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

actual class SettingsDataStore {
    private val context: Context by lazy {
        org.koin.mp.KoinPlatform.getKoin().get<Context>()
    }

    companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val THEME = stringPreferencesKey("reader_theme")
        val LAYOUT_MODE = stringPreferencesKey("layout_mode")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        // Legacy keys kept for migration
        private val BACKGROUND_COLOR_LEGACY = androidx.datastore.preferences.core.longPreferencesKey("background_color")
    }

    actual val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        val themeKey = prefs[THEME]
        val theme = if (themeKey != null) {
            ReaderTheme.fromKey(themeKey)
        } else {
            val legacyBg = prefs[BACKGROUND_COLOR_LEGACY]
            if (legacyBg != null) ReaderTheme.fromLegacyBackgroundColor(legacyBg) else ReaderTheme.DEFAULT_WHITE
        }
        ReaderSettings(
            fontSize = prefs[FONT_SIZE] ?: 16f,
            lineHeight = prefs[LINE_HEIGHT] ?: 1.5f,
            theme = theme,
            language = prefs[stringPreferencesKey("language")] ?: "zh",
            layoutMode = LayoutMode.fromKey(prefs[LAYOUT_MODE]),
            sortOrder = SortOrder.fromKey(prefs[SORT_ORDER])
        )
    }

    actual suspend fun updateFontSize(size: Float) {
        context.dataStore.edit { it[FONT_SIZE] = size }
    }

    actual suspend fun updateLineHeight(height: Float) {
        context.dataStore.edit { it[LINE_HEIGHT] = height }
    }

    actual suspend fun updateTheme(theme: ReaderTheme) {
        context.dataStore.edit { it[THEME] = theme.key }
    }

    actual suspend fun updateLanguage(language: String) {
        context.dataStore.edit { it[stringPreferencesKey("language")] = language }
    }

    actual suspend fun updateLayoutMode(layoutMode: LayoutMode) {
        context.dataStore.edit { it[LAYOUT_MODE] = layoutMode.key }
    }

    actual suspend fun updateSortOrder(sortOrder: SortOrder) {
        context.dataStore.edit { it[SORT_ORDER] = sortOrder.key }
    }
}
