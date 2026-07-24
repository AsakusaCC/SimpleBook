package com.ebookreader.simplebook.platform

import android.content.Context
import com.ebookreader.simplebook.domain.service.SyncForegroundService
import org.koin.mp.KoinPlatform

actual class ForegroundSyncController actual constructor() {
    private val context: Context by lazy {
        KoinPlatform.getKoin().get<Context>()
    }

    actual fun start() {
        SyncForegroundService.start(context)
    }

    actual fun stop() {
        SyncForegroundService.stop(context)
    }
}
