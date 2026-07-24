package com.ebookreader.simplebook.domain.model

data class ReaderSettings(
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val theme: ReaderTheme = ReaderTheme.DEFAULT_WHITE,
    val language: String = "zh",
    val layoutMode: LayoutMode = LayoutMode.LARGE_GRID,
    val sortOrder: SortOrder = SortOrder.LAST_READ
) {
    val backgroundColor: Long get() = theme.backgroundColor
    val textColor: Long get() = theme.textColor
}
