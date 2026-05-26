package com.ebookreader.simplebook.ui.sync

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncService: SyncService,
    val authManager: AuthManager,
    private val syncLogDao: SyncLogDao
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    val lastSyncedAt: StateFlow<Long?> = syncService.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncLogs: StateFlow<List<SyncLogEntity>> = syncLogDao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isSignedIn: Boolean get() = authManager.isSignedIn
    val accountEmail: String? get() = authManager.signedInAccount.value?.email

    fun getSignInIntent(): Intent = authManager.signInIntent

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    fun handleSignInResult(data: Intent) {
        viewModelScope.launch {
            val result = authManager.handleSignInResult(data)
            if (result.isFailure) {
                _signInError.value = result.exceptionOrNull()?.message ?: "Sign-in failed"
            } else {
                _signInError.value = null
            }
        }
    }

    fun clearSignInError() { _signInError.value = null }
    fun setSignInError(message: String) { _signInError.value = message }

    fun syncNow() {
        viewModelScope.launch { syncService.syncAll() }
    }

    fun signOut() { authManager.signOut() }
}
