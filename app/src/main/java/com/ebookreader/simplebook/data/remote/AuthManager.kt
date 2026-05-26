package com.ebookreader.simplebook.data.remote

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    val isSignedIn: Boolean get() = _signedInAccount.value != null

    val signInIntent: Intent get() = signInClient.signInIntent

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    init {
        _signedInAccount.value = GoogleSignIn.getLastSignedInAccount(context)
        Log.d("AuthManager", "init: lastSignedIn=${_signedInAccount.value?.email}, isSignedIn=$isSignedIn")
    }

    suspend fun handleSignInResult(data: Intent): Result<GoogleSignInAccount> {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            _signedInAccount.value = account
            _signInError.value = null
            Result.success(account)
        } catch (e: ApiException) {
            _signInError.value = "${e.statusCode}: ${e.message}"
            Result.failure(e)
        }
    }

    fun clearSignInError() {
        _signInError.value = null
    }

    fun signOut() {
        signInClient.signOut()
        _signedInAccount.value = null
    }
}
