package com.ebookreader.simplebook.data.local

import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

actual class SettingsDataStore {
    private val propsFile = File(System.getProperty("user.home"), "Library/SimpleBook/settings.properties")
    private val props = Properties().apply {
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
    }

    private val _settings = MutableStateFlow(loadSettings())
    actual val settings: Flow<ReaderSettings> = _settings.asStateFlow()

    private fun loadSettings(): ReaderSettings {
        return ReaderSettings(
            fontSize = props.getProperty("font_size", "16").toFloat(),
            lineHeight = props.getProperty("line_height", "1.5").toFloat(),
            theme = ReaderTheme.entries.find { it.key == props.getProperty("theme") }
                ?: ReaderTheme.DEFAULT_WHITE,
            language = props.getProperty("language", "zh"),
            layoutMode = LayoutMode.entries.find { it.key == props.getProperty("layout_mode") }
                ?: LayoutMode.LARGE_GRID,
            sortOrder = SortOrder.entries.find { it.key == props.getProperty("sort_order") }
                ?: SortOrder.LAST_READ
        )
    }

    private fun save() {
        propsFile.parentFile.mkdirs()
        // Non-sensitive prefs (font/theme/language) — no owner-only hardening.
        propsFile.outputStream().use { props.store(it, null) }
    }

    actual suspend fun updateFontSize(size: Float) {
        props["font_size"] = size.toString()
        save()
        _settings.value = loadSettings()
    }

    actual suspend fun updateLineHeight(height: Float) {
        props["line_height"] = height.toString()
        save()
        _settings.value = loadSettings()
    }

    actual suspend fun updateTheme(theme: ReaderTheme) {
        props["theme"] = theme.key
        save()
        _settings.value = loadSettings()
    }

    actual suspend fun updateLanguage(language: String) {
        props["language"] = language
        save()
        _settings.value = loadSettings()
    }

    actual suspend fun updateLayoutMode(layoutMode: LayoutMode) {
        props["layout_mode"] = layoutMode.key
        save()
        _settings.value = loadSettings()
    }

    actual suspend fun updateSortOrder(sortOrder: SortOrder) {
        props["sort_order"] = sortOrder.key
        save()
        _settings.value = loadSettings()
    }
}
