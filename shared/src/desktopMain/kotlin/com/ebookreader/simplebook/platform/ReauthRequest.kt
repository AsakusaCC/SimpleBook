package com.ebookreader.simplebook.platform

actual class ReauthRequest actual constructor(
    val cause: Throwable
)

actual fun Throwable.toReauthRequest(): ReauthRequest? =
    if (this is AuthExpiredException) ReauthRequest(this) else null
