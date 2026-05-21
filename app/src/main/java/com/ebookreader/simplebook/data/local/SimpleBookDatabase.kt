package com.ebookreader.simplebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class SimpleBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
}
