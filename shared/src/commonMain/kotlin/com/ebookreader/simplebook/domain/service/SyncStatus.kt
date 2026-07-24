package com.ebookreader.simplebook.domain.service

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Success : SyncStatus()
}
