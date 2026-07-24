package com.ebookreader.simplebook.data.local

import androidx.room.RoomDatabase

expect fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase>
