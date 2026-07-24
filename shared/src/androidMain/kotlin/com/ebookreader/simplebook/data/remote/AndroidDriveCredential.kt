package com.ebookreader.simplebook.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequest
import com.google.api.services.drive.DriveScopes

class AndroidDriveCredential(
    context: Context,
    account: GoogleSignInAccount
) : DriveCredential {
    private val credential = GoogleAccountCredential.usingOAuth2(
        context, listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE)
    ).also { it.selectedAccount = account.account }

    override fun initialize(request: HttpRequest) {
        credential.initialize(request)
    }
}
