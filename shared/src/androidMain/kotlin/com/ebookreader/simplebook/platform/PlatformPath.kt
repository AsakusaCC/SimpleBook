package com.ebookreader.simplebook.platform

import android.content.Context
import org.koin.mp.KoinPlatform

actual fun getBooksDir(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return java.io.File(context.filesDir, "books").absolutePath
}

actual fun getDatabaseDir(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.getDatabasePath("simplebook.db").parent!!
}

actual fun getCacheDir(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.cacheDir.absolutePath
}
