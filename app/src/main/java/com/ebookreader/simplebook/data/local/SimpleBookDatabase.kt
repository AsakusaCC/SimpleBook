package com.ebookreader.simplebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // --- books: new PK is uuid ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS books_new (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        author TEXT NOT NULL DEFAULT '',
                        filePath TEXT NOT NULL,
                        format TEXT NOT NULL,
                        coverPath TEXT,
                        fileSize INTEGER NOT NULL DEFAULT 0,
                        addedAt INTEGER NOT NULL,
                        lastReadAt INTEGER,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        lastSyncedAt INTEGER,
                        driveFileId TEXT
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO books_new (uuid, title, author, filePath, format, coverPath, fileSize, addedAt, lastReadAt, updatedAt, isDeleted, lastSyncedAt, driveFileId)
                       SELECT lower(hex(randomblob(16))), title, author, filePath, format, coverPath, fileSize, addedAt, lastReadAt, strftime('%s','now')*1000, 0, lastSyncedAt, driveFileId FROM books""".trimIndent()
                )
                db.execSQL("DROP TABLE books")
                db.execSQL("ALTER TABLE books_new RENAME TO books")

                // --- reading_progress ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS reading_progress_new (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        bookUuid TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL DEFAULT 0,
                        charOffset INTEGER NOT NULL DEFAULT 0,
                        percentage REAL NOT NULL DEFAULT 0.0,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO reading_progress_new (uuid, bookUuid, chapterIndex, charOffset, percentage, updatedAt, isDeleted)
                       SELECT lower(hex(randomblob(16))), b.uuid, rp.chapterIndex, rp.charOffset, rp.percentage, rp.updatedAt, 0
                       FROM reading_progress rp JOIN books b ON rp.bookId = b.uuid""".trimIndent()
                )
                db.execSQL("DROP TABLE reading_progress")
                db.execSQL("ALTER TABLE reading_progress_new RENAME TO reading_progress")

                // --- bookmarks ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS bookmarks_new (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        bookUuid TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL DEFAULT 0,
                        charOffset INTEGER NOT NULL DEFAULT 0,
                        name TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO bookmarks_new (uuid, bookUuid, chapterIndex, charOffset, name, createdAt, updatedAt, isDeleted)
                       SELECT lower(hex(randomblob(16))), b.uuid, bm.chapterIndex, bm.charOffset, bm.name, bm.createdAt, strftime('%s','now')*1000, 0
                       FROM bookmarks bm JOIN books b ON bm.bookId = b.uuid""".trimIndent()
                )
                db.execSQL("DROP TABLE bookmarks")
                db.execSQL("ALTER TABLE bookmarks_new RENAME TO bookmarks")

                // --- highlights ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS highlights_new (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        bookUuid TEXT NOT NULL,
                        chapterIndex INTEGER NOT NULL DEFAULT 0,
                        startOffset INTEGER NOT NULL,
                        endOffset INTEGER NOT NULL,
                        color INTEGER NOT NULL,
                        note TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO highlights_new (uuid, bookUuid, chapterIndex, startOffset, endOffset, color, note, createdAt, updatedAt, isDeleted)
                       SELECT lower(hex(randomblob(16))), b.uuid, hl.chapterIndex, hl.startOffset, hl.endOffset, hl.color, hl.note, hl.createdAt, strftime('%s','now')*1000, 0
                       FROM highlights hl JOIN books b ON hl.bookId = b.uuid""".trimIndent()
                )
                db.execSQL("DROP TABLE highlights")
                db.execSQL("ALTER TABLE highlights_new RENAME TO highlights")

                // --- notes (需要映射 highlightId 到 highlight uuid) ---
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS notes_new (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        bookUuid TEXT NOT NULL,
                        highlightUuid TEXT,
                        chapterIndex INTEGER NOT NULL DEFAULT 0,
                        charOffset INTEGER NOT NULL DEFAULT 0,
                        content TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE,
                        FOREIGN KEY(highlightUuid) REFERENCES highlights(uuid) ON DELETE SET NULL
                    )""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO notes_new (uuid, bookUuid, highlightUuid, chapterIndex, charOffset, content, createdAt, updatedAt, isDeleted)
                       SELECT lower(hex(randomblob(16))), b.uuid, h.uuid, n.chapterIndex, n.charOffset, n.content, n.createdAt, strftime('%s','now')*1000, 0
                       FROM notes n
                       JOIN books b ON n.bookId = b.uuid
                       LEFT JOIN highlights h ON n.highlightId = h.uuid""".trimIndent()
                )
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                // --- 替换 conflict_records 为 sync_logs ---
                db.execSQL("DROP TABLE IF EXISTS conflict_records")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS sync_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookUuid TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        entityUuid TEXT NOT NULL,
                        action TEXT NOT NULL,
                        localUpdatedAt INTEGER,
                        remoteUpdatedAt INTEGER,
                        resolvedAt INTEGER NOT NULL,
                        FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
                    )""".trimIndent()
                )

                // --- 索引 ---
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookUuid ON bookmarks(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_bookUuid ON highlights(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_bookUuid ON notes(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_highlightUuid ON notes(highlightUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_progress_bookUuid ON reading_progress(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_logs_bookUuid ON sync_logs(bookUuid)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS folders (
                        uuid TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        lastSyncedAt INTEGER,
                        driveFileId TEXT
                    )
                """.trimIndent())
                db.execSQL("ALTER TABLE books ADD COLUMN folderId TEXT")
            }
        }
    }
}
