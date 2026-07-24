package com.ebookreader.simplebook.platform

expect class AuthProvider() {
    val isSignedIn: Boolean
    val userEmail: String?

    /** 返回 access token；失败返回 Result.failure */
    suspend fun signIn(): Result<String>

    fun signOut()
}
