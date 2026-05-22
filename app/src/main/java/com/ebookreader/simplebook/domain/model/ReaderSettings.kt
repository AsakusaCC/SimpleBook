package com.ebookreader.simplebook.domain.model

data class ReaderSettings(
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val backgroundColor: Long = 0xFFFFFFFF,
    val textColor: Long = 0xFF000000
)
