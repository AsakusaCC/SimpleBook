package com.ebookreader.simplebook.domain.model

enum class SortOrder(val key: String) {
    LAST_READ("last_read"),
    NAME("name");

    companion object {
        fun fromKey(key: String?): SortOrder =
            entries.find { it.key == key } ?: LAST_READ
    }
}
