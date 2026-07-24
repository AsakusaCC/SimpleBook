package com.ebookreader.simplebook.data.remote

import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.HttpRequest

class DesktopDriveCredential(
    private val accessToken: String
) : DriveCredential {
    private val credential = Credential(BearerToken.authorizationHeaderAccessMethod()).apply {
        setAccessToken(accessToken)
    }

    override fun initialize(request: HttpRequest) {
        credential.initialize(request)
        // Explicitly set the Authorization header. The Credential/BearerToken interceptor
        // chain is *supposed* to add it at execute() time, but Drive was returning 403
        // "unregistered callers" — the header wasn't reaching the request. Set it directly.
        request.headers.authorization = "Bearer $accessToken"
    }
}
