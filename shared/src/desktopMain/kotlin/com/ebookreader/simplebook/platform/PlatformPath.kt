package com.ebookreader.simplebook.platform

import java.io.File

actual fun getBooksDir(): String {
    val dir = File(System.getProperty("user.home"), "Library/SimpleBook/books")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun getDatabaseDir(): String {
    val dir = File(System.getProperty("user.home"), "Library/SimpleBook/database")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun getCacheDir(): String {
    val dir = File(System.getProperty("user.home"), "Library/SimpleBook/cache")
    dir.mkdirs()
    return dir.absolutePath
}
