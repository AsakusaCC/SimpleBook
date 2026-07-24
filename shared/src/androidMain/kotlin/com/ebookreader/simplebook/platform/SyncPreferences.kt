package com.ebookreader.simplebook.platform

import android.content.Context
import org.koin.mp.KoinPlatform

actual class SyncPreferences actual constructor() {
    private val prefs by lazy {
        KoinPlatform.getKoin().get<Context>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    }

    actual fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    actual fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    actual fun getStringSet(key: String, default: Set<String>?): Set<String>? = prefs.getStringSet(key, default)
    actual fun putStringSet(key: String, value: Set<String>) =
        prefs.edit().putStringSet(key, value).apply()
}
