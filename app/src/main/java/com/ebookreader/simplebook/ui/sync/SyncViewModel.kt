package com.ebookreader.simplebook.ui.sync

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.dao.ConflictDao
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncService: SyncService,
    val authManager: AuthManager,
    private val conflictDao: ConflictDao
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    val conflictCount: StateFlow<Int> = syncService.conflictCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isSignedIn: Boolean get() = authManager.isSignedIn

    val accountEmail: String? get() = authManager.signedInAccount.value?.email

    fun getSignInIntent(): Intent = authManager.signInIntent

    fun handleSignInResult(data: Intent) {
        viewModelScope.launch {
            authManager.handleSignInResult(data)
        }
    }

    fun syncNow() {
        viewModelScope.launch { syncService.syncAll() }
    }

    fun resolveConflict(conflictId: Long, useRemote: Boolean) {
        viewModelScope.launch { syncService.resolveConflict(conflictId, useRemote) }
    }

    fun resolveAllConflicts(useRemote: Boolean) {
        viewModelScope.launch { syncService.resolveAllConflicts(useRemote) }
    }

    fun signOut() {
        authManager.signOut()
    }
}
