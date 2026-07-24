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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

class AuthManager(
    private val context: Context
) {
    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE))
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

    /**
     * 处理 Sign-In intent 的 Activity 回调结果。
     * 返回 null 表示登录成功；非 null 为错误信息（含 ApiException statusCode）。
     *
     * 刻意不返回 Result<GoogleSignInAccount>：play-services-auth 仅是 shared/androidMain 的
     * implementation 依赖（不传递到 androidApp），若返回值泛型实参泄露 GoogleSignInAccount，
     * androidApp 模块在表达式类型里引用不到它，会触发 Kotlin 的“may be forbidden soon”警告。
     * 登录后的 account 仍可通过 [signedInAccount] StateFlow 读取。
     */
    suspend fun handleSignInResult(data: Intent): String? {
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            _signedInAccount.value = account
            _signInError.value = null
            null
        } catch (e: ApiException) {
            val msg = "${e.statusCode}: ${e.message}"
            _signInError.value = msg
            msg
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
