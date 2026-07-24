package com.ebookreader.simplebook.data.parser

import com.ebookreader.simplebook.domain.model.Chapter

data class TxtParseResult(
    val title: String,
    val author: String = "",
    val chapters: List<Chapter>,
    val encoding: String
)
