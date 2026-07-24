package com.ebookreader.simplebook.platform

import android.util.Log

actual fun logD(tag: String, message: String) { Log.d(tag, message) }
actual fun logW(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) Log.w(tag, message, throwable) else Log.w(tag, message)
}
actual fun logE(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) Log.e(tag, message, throwable) else Log.e(tag, message)
}
