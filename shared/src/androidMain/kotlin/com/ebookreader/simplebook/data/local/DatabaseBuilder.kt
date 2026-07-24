package com.ebookreader.simplebook.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.mp.KoinPlatform

actual fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase> {
    val context = KoinPlatform.getKoin().get<Context>()
    return Room.databaseBuilder(
        context,
        SimpleBookDatabase::class.java,
        "simplebook.db"
    )
}
