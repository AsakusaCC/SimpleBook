package com.ebookreader.simplebook.platform

import com.ebookreader.simplebook.data.remote.AuthManager

actual class AuthProvider actual constructor() {
    private val authManager: AuthManager by lazy {
        org.koin.mp.KoinPlatform.getKoin().get<AuthManager>()
    }

    actual val isSignedIn: Boolean get() = authManager.isSignedIn
    actual val userEmail: String? get() = authManager.signedInAccount.value?.email

    actual suspend fun signIn(): Result<String> {
        // Android 端实际的 Sign-In 流程由 MainActivity 的 signInLauncher 触发（UI 层）
        // 这里返回当前已登录 account 的占位 token
        val account = authManager.signedInAccount.value
            ?: return Result.failure(Exception("Not signed in"))
        return Result.success("android_session")
    }

    actual fun signOut() {
        authManager.signOut()
    }
}
