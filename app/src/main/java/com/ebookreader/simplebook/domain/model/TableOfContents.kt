package com.ebookreader.simplebook.domain.model

data class TocEntry(
    val title: String,
    val href: String = "",
    val chapterIndex: Int = -1,
    val children: List<TocEntry> = emptyList()
)

data class TableOfContents(
    val entries: List<TocEntry>
)
