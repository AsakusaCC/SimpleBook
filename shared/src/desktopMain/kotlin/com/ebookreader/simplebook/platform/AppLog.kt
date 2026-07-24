package com.ebookreader.simplebook.platform

actual fun logD(tag: String, message: String) {
    println("[$tag] $message")
}

actual fun logW(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] WARN: $message")
    throwable?.printStackTrace(System.err)
}

actual fun logE(tag: String, message: String, throwable: Throwable?) {
    System.err.println("[$tag] ERROR: $message")
    throwable?.printStackTrace(System.err)
}
