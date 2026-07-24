package com.ebookreader.simplebook.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import java.io.File

actual fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase> {
    val dbDir = File(System.getProperty("user.home"), "Library/SimpleBook/database")
    dbDir.mkdirs()
    // Room KMP on JVM requires an explicit SQLiteDriver (unlike Android, whose
    // context-aware builder auto-configures FrameworkSQLiteDriver). BundledSQLiteDriver
    // ships a native SQLite via androidx.sqlite:sqlite-bundled.
    return Room.databaseBuilder<SimpleBookDatabase>(
        name = File(dbDir, "simplebook.db").absolutePath
    ).setDriver(BundledSQLiteDriver())
}
