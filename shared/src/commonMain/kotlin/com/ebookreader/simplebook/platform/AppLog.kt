package com.ebookreader.simplebook.platform

expect fun logD(tag: String, message: String)
expect fun logW(tag: String, message: String, throwable: Throwable? = null)
expect fun logE(tag: String, message: String, throwable: Throwable? = null)
