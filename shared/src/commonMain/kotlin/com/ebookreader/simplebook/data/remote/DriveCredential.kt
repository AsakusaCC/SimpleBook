package com.ebookreader.simplebook.data.remote

import com.google.api.client.http.HttpRequest

interface DriveCredential {
    fun initialize(request: HttpRequest)
}
