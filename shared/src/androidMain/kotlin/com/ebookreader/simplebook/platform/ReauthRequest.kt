package com.ebookreader.simplebook.platform

import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException

actual class ReauthRequest actual constructor(
    val cause: Throwable
)

actual fun Throwable.toReauthRequest(): ReauthRequest? {
    return if (this is UserRecoverableAuthIOException) ReauthRequest(this) else null
}

/** 从 cause 窄化取出待启动的 reauth Intent；cause 不是 UserRecoverableAuthIOException 时返回 null。 */
fun ReauthRequest.toIntent(): android.content.Intent? =
    (cause as? UserRecoverableAuthIOException)?.intent
