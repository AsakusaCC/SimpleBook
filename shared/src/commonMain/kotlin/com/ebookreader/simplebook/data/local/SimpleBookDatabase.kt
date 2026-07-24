package com.ebookreader.simplebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.FolderDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity
import com.ebookreader.simplebook.data.local.entity.FolderEntity
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        SyncLogEntity::class,
        FolderEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SimpleBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun folderDao(): FolderDao
}
