package com.ebookreader.simplebook.data.parser

import com.ebookreader.simplebook.domain.model.TableOfContents
import nl.siegmann.epublib.domain.Book as EpubBook

data class EpubParseResult(
    val title: String,
    val author: String,
    val coverPath: String?,
    val tableOfContents: TableOfContents,
    val chapterCount: Int,
    // Keep reference to epubBook for lazy chapter loading
    @Transient val epubBook: EpubBook? = null
)
