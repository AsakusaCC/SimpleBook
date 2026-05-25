package com.ebookreader.simplebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.ConflictDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import com.ebookreader.simplebook.data.local.entity.ConflictRecordEntity
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        ConflictRecordEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class SimpleBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun conflictDao(): ConflictDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE books ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE books ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN driveFileId TEXT")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE highlights ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE highlights ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE notes ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS conflict_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId INTEGER NOT NULL,
                        localSyncVersion INTEGER NOT NULL,
                        remoteSyncVersion INTEGER NOT NULL,
                        localData TEXT NOT NULL,
                        remoteData TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        resolvedAt INTEGER,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
