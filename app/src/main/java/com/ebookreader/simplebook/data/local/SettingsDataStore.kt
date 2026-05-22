package com.ebookreader.simplebook.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ebookreader.simplebook.domain.model.ReaderSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "reader_settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        val FONT_SIZE = floatPreferencesKey("font_size")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val BACKGROUND_COLOR = longPreferencesKey("background_color")
        val TEXT_COLOR = longPreferencesKey("text_color")
    }

    val settings: Flow<ReaderSettings> = context.dataStore.data.map { prefs ->
        ReaderSettings(
            fontSize = prefs[FONT_SIZE] ?: 16f,
            lineHeight = prefs[LINE_HEIGHT] ?: 1.5f,
            backgroundColor = prefs[BACKGROUND_COLOR] ?: 0xFFFFFFFF,
            textColor = prefs[TEXT_COLOR] ?: 0xFF000000
        )
    }

    suspend fun updateFontSize(size: Float) {
        context.dataStore.edit { it[FONT_SIZE] = size }
    }

    suspend fun updateLineHeight(height: Float) {
        context.dataStore.edit { it[LINE_HEIGHT] = height }
    }

    suspend fun updateBackgroundColor(color: Long) {
        context.dataStore.edit { it[BACKGROUND_COLOR] = color }
    }

    suspend fun updateTextColor(color: Long) {
        context.dataStore.edit { it[TEXT_COLOR] = color }
    }
}
