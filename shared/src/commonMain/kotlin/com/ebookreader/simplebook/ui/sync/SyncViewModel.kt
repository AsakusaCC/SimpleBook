package com.ebookreader.simplebook.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
import com.ebookreader.simplebook.platform.AuthProvider
import com.ebookreader.simplebook.platform.ForegroundSyncController
import com.ebookreader.simplebook.platform.ReauthRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SyncViewModel(
    private val syncService: SyncService,
    val authProvider: AuthProvider,
    private val syncLogDao: SyncLogDao,
    private val foregroundSyncController: ForegroundSyncController
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    val lastSyncedAt: StateFlow<Long?> = syncService.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val reauthRequest: StateFlow<ReauthRequest?> = syncService.reauthRequest
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun consumeReauthRequest() {
        syncService.consumeReauthRequest()
    }

    val syncLogs: StateFlow<List<SyncLogEntity>> = syncLogDao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Exposed as StateFlow so the UI recomposes on sign-in / sign-out. authProvider's
    // accessToken is a plain var — reading authProvider.isSignedIn directly in a composable
    // does NOT trigger recomposition, which is why the sign-in button never updated after a
    // successful OAuth (the original "no reaction" bug).
    private val _isSignedIn = MutableStateFlow(authProvider.isSignedIn)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private val _accountEmail = MutableStateFlow(authProvider.userEmail)
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    fun refreshAuthState() {
        _isSignedIn.value = authProvider.isSignedIn
        _accountEmail.value = authProvider.userEmail
    }

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    fun clearSignInError() { _signInError.value = null }
    fun setSignInError(message: String) { _signInError.value = message }

    fun signIn() {
        viewModelScope.launch {
            _signInError.value = null
            val result = authProvider.signIn()
            refreshAuthState()
            if (result.isSuccess) {
                syncNow()   // 登录成功后自动同步（闭合 reauth 恢复回路，亦适用首次登录）
            } else {
                result.exceptionOrNull()?.let {
                    _signInError.value = it.message ?: "登录失败"
                }
            }
        }
    }

    fun syncNow() {
        foregroundSyncController.start()
        viewModelScope.launch { syncService.syncAll() }
    }

    fun signOut() {
        authProvider.signOut()
        refreshAuthState()
    }
}
