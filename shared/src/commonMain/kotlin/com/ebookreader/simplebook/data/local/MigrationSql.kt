package com.ebookreader.simplebook.data.local

/**
 * 数据库迁移 SQL（v1 → v4），以 [List<String>] 形式集中存放于 commonMain，
 * 供 androidMain 的 Room [androidx.room.migration.Migration] 与 desktopTest
 * 的 sqlite-jdbc 冒烟测试**共用同一份**，避免第二真相。
 *
 * 每个元素是一条独立的 SQL 语句（[androidx.sqlite.db.SupportSQLiteDatabase.execSQL]
 * 与 java.sql.Statement.execute 均按单条执行）。
 */
object MigrationSql {

    /**
     * v1 → v2：为同步功能扩展字段（syncVersion / lastSyncedAt / driveFileId），
     * 并新建 conflict_records 表。
     */
    val MIGRATION_1_2: List<String> = listOf(
        "ALTER TABLE books ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE books ADD COLUMN lastSyncedAt INTEGER",
        "ALTER TABLE books ADD COLUMN driveFileId TEXT",
        "ALTER TABLE reading_progress ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE reading_progress ADD COLUMN lastSyncedAt INTEGER",
        "ALTER TABLE bookmarks ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE bookmarks ADD COLUMN lastSyncedAt INTEGER",
        "ALTER TABLE highlights ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE highlights ADD COLUMN lastSyncedAt INTEGER",
        "ALTER TABLE notes ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1",
        "ALTER TABLE notes ADD COLUMN lastSyncedAt INTEGER",
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

    /**
     * v2 → v3：主键从 INTEGER id 迁移到 TEXT uuid，并引入软删除（isDeleted/updatedAt）。
     *
     * **Approach B（版本无关、API 26 / SQLite 3.19 安全）**：
     * - 父表（books、highlights）：`RENAME 旧表 TO _old` 让位 → 用【最终名 + 最终 schema】新建 → 从旧表+映射填数据。
     * - 叶子表（reading_progress、bookmarks、notes）：建 `_new`（FK→已就位的父表）→ 填 → DROP 旧 → RENAME。
     * - 子表 FK 从 CREATE 就指向最终名 `books`/`highlights`，**不依赖 `ALTER TABLE RENAME` 的 FK retarget**
     *   （该特性 SQLite 3.25+ 才有，minSdk=26 对应 3.19，依赖它会在真机留下指向 `books_new` 的悬空 FK → Room schemaHash 不匹配）。
     * - 不使用 `PRAGMA foreign_keys=OFF`（Room 迁移在事务内，该 pragma 是 no-op）。
     * - `DROP TABLE books` 在 FK=ON 下会 CASCADE 删子表，故父表用"rename 让位 + 最终名重建"而非"DROP + RENAME _new"，
     *   且子表数据迁移发生在父表以最终名就位之后。
     * - books.uuid / highlights.uuid 均取自映射表（highlights 尤其关键，保证 notes.highlightUuid 能匹配）。
     *
     * uuid 用 `lower(hex(randomblob(16)))`（32-hex，与历史迁移一致）；与运行时 `java.util.UUID.randomUUID()`
     * （36-hyphen）格式不同，但功能无害（唯一 TEXT PK、FK 自洽、Drive `book_{uuid}` 均可用）。
     */
    val MIGRATION_2_3: List<String> = listOf(
        // 1) id→uuid 映射表（旧表完好时先建）
        "CREATE TABLE _book_id_map(old_id INTEGER PRIMARY KEY, new_uuid TEXT NOT NULL)",
        "INSERT INTO _book_id_map(old_id, new_uuid) SELECT id, lower(hex(randomblob(16))) FROM books",
        "CREATE TABLE _hl_id_map(old_id INTEGER PRIMARY KEY, new_uuid TEXT NOT NULL)",
        "INSERT INTO _hl_id_map(old_id, new_uuid) SELECT id, lower(hex(randomblob(16))) FROM highlights",

        // 2) conflict_records 被 sync_logs 取代；叶子表，直接删（无外向级联）
        "DROP TABLE conflict_records",

        // 3) 重建 books：旧表 RENAME 让位 → 以【最终名 books】+ uuid PK 新建 → 拷贝
        "ALTER TABLE books RENAME TO _books_old",
        """
        CREATE TABLE IF NOT EXISTS books (
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
        )
        """.trimIndent(),
        """
        INSERT INTO books (uuid, title, author, filePath, format, coverPath, fileSize, addedAt, lastReadAt, updatedAt, isDeleted, lastSyncedAt, driveFileId)
        SELECT m.new_uuid, b.title, b.author, b.filePath, b.format, b.coverPath, b.fileSize, b.addedAt, b.lastReadAt, strftime('%s','now')*1000, 0, b.lastSyncedAt, b.driveFileId
        FROM _books_old b JOIN _book_id_map m ON b.id = m.old_id
        """.trimIndent(),

        // 4) 重建 highlights（notes 的父表）：让位 → FK→books(uuid) → uuid 取自映射（供 notes 引用）
        "ALTER TABLE highlights RENAME TO _highlights_old",
        """
        CREATE TABLE IF NOT EXISTS highlights (
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
        )
        """.trimIndent(),
        """
        INSERT INTO highlights (uuid, bookUuid, chapterIndex, startOffset, endOffset, color, note, createdAt, updatedAt, isDeleted)
        SELECT hm.new_uuid, bm.new_uuid, h.chapterIndex, h.startOffset, h.endOffset, h.color, h.note, h.createdAt, strftime('%s','now')*1000, 0
        FROM _highlights_old h JOIN _hl_id_map hm ON h.id = hm.old_id JOIN _book_id_map bm ON h.bookId = bm.old_id
        """.trimIndent(),

        // 5) reading_progress（叶子，FK→books 最终名）
        """
        CREATE TABLE IF NOT EXISTS reading_progress_new (
            uuid TEXT NOT NULL PRIMARY KEY,
            bookUuid TEXT NOT NULL,
            chapterIndex INTEGER NOT NULL DEFAULT 0,
            charOffset INTEGER NOT NULL DEFAULT 0,
            percentage REAL NOT NULL DEFAULT 0.0,
            updatedAt INTEGER NOT NULL,
            isDeleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        INSERT INTO reading_progress_new (uuid, bookUuid, chapterIndex, charOffset, percentage, updatedAt, isDeleted)
        SELECT lower(hex(randomblob(16))), m.new_uuid, rp.chapterIndex, rp.charOffset, rp.percentage, rp.updatedAt, 0
        FROM reading_progress rp JOIN _book_id_map m ON rp.bookId = m.old_id
        """.trimIndent(),
        "DROP TABLE reading_progress",
        "ALTER TABLE reading_progress_new RENAME TO reading_progress",

        // 6) bookmarks（叶子）
        """
        CREATE TABLE IF NOT EXISTS bookmarks_new (
            uuid TEXT NOT NULL PRIMARY KEY,
            bookUuid TEXT NOT NULL,
            chapterIndex INTEGER NOT NULL DEFAULT 0,
            charOffset INTEGER NOT NULL DEFAULT 0,
            name TEXT NOT NULL DEFAULT '',
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            isDeleted INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
        )
        """.trimIndent(),
        """
        INSERT INTO bookmarks_new (uuid, bookUuid, chapterIndex, charOffset, name, createdAt, updatedAt, isDeleted)
        SELECT lower(hex(randomblob(16))), m.new_uuid, bm.chapterIndex, bm.charOffset, bm.name, bm.createdAt, strftime('%s','now')*1000, 0
        FROM bookmarks bm JOIN _book_id_map m ON bm.bookId = m.old_id
        """.trimIndent(),
        "DROP TABLE bookmarks",
        "ALTER TABLE bookmarks_new RENAME TO bookmarks",

        // 7) notes（叶子，FK→books(uuid) + highlights(uuid)，均最终名；LEFT JOIN 保留无 highlight 的 note）
        """
        CREATE TABLE IF NOT EXISTS notes_new (
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
        )
        """.trimIndent(),
        """
        INSERT INTO notes_new (uuid, bookUuid, highlightUuid, chapterIndex, charOffset, content, createdAt, updatedAt, isDeleted)
        SELECT lower(hex(randomblob(16))), bm.new_uuid, hm.new_uuid, n.chapterIndex, n.charOffset, n.content, n.createdAt, strftime('%s','now')*1000, 0
        FROM notes n JOIN _book_id_map bm ON n.bookId = bm.old_id LEFT JOIN _hl_id_map hm ON n.highlightId = hm.old_id
        """.trimIndent(),
        "DROP TABLE notes",
        "ALTER TABLE notes_new RENAME TO notes",

        // 8) sync_logs（空表，取代 conflict_records）
        """
        CREATE TABLE IF NOT EXISTS sync_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            bookUuid TEXT NOT NULL,
            entityType TEXT NOT NULL,
            entityUuid TEXT NOT NULL,
            action TEXT NOT NULL,
            localUpdatedAt INTEGER,
            remoteUpdatedAt INTEGER,
            resolvedAt INTEGER NOT NULL,
            FOREIGN KEY(bookUuid) REFERENCES books(uuid) ON DELETE CASCADE
        )
        """.trimIndent(),

        // 9) 清理临时表（此时无任何表再引用它们，DROP 不触发级联）
        "DROP TABLE _highlights_old",
        "DROP TABLE _books_old",
        "DROP TABLE _book_id_map",
        "DROP TABLE _hl_id_map",

        // 10) 索引（命名与 Room 自动命名 index_<表>_<列> 一致，schemaHash 校验需要）
        "CREATE INDEX IF NOT EXISTS index_reading_progress_bookUuid ON reading_progress(bookUuid)",
        "CREATE INDEX IF NOT EXISTS index_bookmarks_bookUuid ON bookmarks(bookUuid)",
        "CREATE INDEX IF NOT EXISTS index_highlights_bookUuid ON highlights(bookUuid)",
        "CREATE INDEX IF NOT EXISTS index_notes_bookUuid ON notes(bookUuid)",
        "CREATE INDEX IF NOT EXISTS index_notes_highlightUuid ON notes(highlightUuid)",
        "CREATE INDEX IF NOT EXISTS index_sync_logs_bookUuid ON sync_logs(bookUuid)"
    )

    /**
     * v3 → v4：新增 folders 表 + books.folderId 列。无数据迁移。
     */
    val MIGRATION_3_4: List<String> = listOf(
        """
        CREATE TABLE IF NOT EXISTS folders (
            uuid TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            createdAt INTEGER NOT NULL,
            updatedAt INTEGER NOT NULL,
            isDeleted INTEGER NOT NULL DEFAULT 0,
            lastSyncedAt INTEGER,
            driveFileId TEXT
        )
        """.trimIndent(),
        "ALTER TABLE books ADD COLUMN folderId TEXT"
    )
}
