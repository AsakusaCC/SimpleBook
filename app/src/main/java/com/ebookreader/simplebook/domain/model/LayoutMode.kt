package com.ebookreader.simplebook.domain.model

enum class LayoutMode(val key: String) {
    LARGE_GRID("large_grid"),
    SMALL_GRID("small_grid"),
    LIST("list");

    companion object {
        fun fromKey(key: String?): LayoutMode =
            entries.find { it.key == key } ?: LARGE_GRID
    }
}
