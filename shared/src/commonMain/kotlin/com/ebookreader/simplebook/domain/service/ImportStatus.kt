package com.ebookreader.simplebook.domain.service

sealed class ImportStatus {
    data object Idle : ImportStatus()
    data object Importing : ImportStatus()
    data class Success(val count: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}
