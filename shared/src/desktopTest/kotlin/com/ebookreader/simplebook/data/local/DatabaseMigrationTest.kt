package com.ebookreader.simplebook.data.local

import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * v1 → v4 迁移冒烟测试：用 sqlite-jdbc 内存库跑 [MigrationSql] 全链路，FK=ON 忠实复现
 * Room 在 Android/BundledSQLite 上的外键行为，验证老用户升级不丢数据。
 *
 * 注意：sqlite-jdbc 默认 `foreign_keys=OFF`，必须在每个连接显式开启，否则
 * `DROP TABLE books` 的级联删除不会触发，迁移 bug 测不出来（测试假绿）。
 */
class DatabaseMigrationTest {

    private lateinit var db: Connection

    @BeforeTest
    fun setup() {
        db = DriverManager.getConnection("jdbc:sqlite::memory:")
        // Room 默认 FK=ON；sqlite-jdbc 默认 OFF，必须显式开启以复现 DROP 级联。
        db.createStatement().use { it.execute("PRAGMA foreign_keys=ON") }
        createV1Schema(db)
        insertV1Data(db)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun v1_to_v4_preserves_all_child_data() {
        runAllMigrations(db)
        assertEquals(2, countRows("books"), "books 应保留 2 行")
        assertEquals(2, countRows("reading_progress"), "reading_progress 应保留 2 行")
        assertEquals(2, countRows("bookmarks"), "bookmarks 应保留 2 行")
        assertEquals(2, countRows("highlights"), "highlights 应保留 2 行")
        assertEquals(2, countRows("notes"), "notes 应保留 2 行")
    }

    @Test
    fun v1_to_v4_links_bookUuid_to_books() {
        runAllMigrations(db)
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM reading_progress rp JOIN books b ON rp.bookUuid = b.uuid"))
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM bookmarks bm JOIN books b ON bm.bookUuid = b.uuid"))
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM highlights h JOIN books b ON h.bookUuid = b.uuid"))
        assertEquals(2, scalarLong("SELECT COUNT(*) FROM notes n JOIN books b ON n.bookUuid = b.uuid"))
    }

    @Test
    fun v1_to_v4_links_notes_highlightUuid_correctly() {
        runAllMigrations(db)
        // 一条 note 链到 highlight、一条 orphan（highlightUuid 为 NULL）
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM notes n JOIN highlights h ON n.highlightUuid = h.uuid"))
        assertEquals(1, scalarLong("SELECT COUNT(*) FROM notes WHERE highlightUuid IS NULL"))
    }

    @Test
    fun v1_to_v4_foreign_keys_reference_final_table_names() {
        runAllMigrations(db)
        // 子表 FK 父表必须是最终名 books / highlights（依赖 RENAME retarget 的写法会在
        // API 26 旧 SQLite 留下 books_new，此断言挡住该类错误）。
        assertEquals(listOf("books"), fkParents("reading_progress"))
        assertEquals(listOf("books"), fkParents("bookmarks"))
        assertEquals(listOf("books"), fkParents("highlights"))
        assertEquals(setOf("books", "highlights"), fkParents("notes").toSet())
        assertEquals(listOf("books"), fkParents("sync_logs"))
    }

    @Test
    fun v1_to_v4_produces_v4_schema() {
        runAllMigrations(db)
        assertTrue(columns("books").contains("folderId"), "books 应含 folderId 列（v4）")
        assertTrue(tableExists("folders"), "应存在 folders 表（v4）")
        assertTrue(tableExists("sync_logs"), "应存在 sync_logs 表（v3）")
    }

    @Test
    fun v1_to_v4_leaves_no_temp_tables_and_no_fk_violations() {
        runAllMigrations(db)
        assertEquals(
            0,
            scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND substr(name,1,1)='_'"),
            "不应残留 _ 临时表"
        )
        assertTrue(queryRows("PRAGMA foreign_key_check").isEmpty(), "不应有 FK 违规")
    }

    // ---------- helpers ----------

    private fun runAllMigrations(c: Connection) {
        c.createStatement().use { st ->
            (MigrationSql.MIGRATION_1_2 + MigrationSql.MIGRATION_2_3 + MigrationSql.MIGRATION_3_4)
                .forEach { st.execute(it) }
        }
    }

    private fun countRows(table: String): Long = scalarLong("SELECT COUNT(*) FROM $table")

    private fun scalarLong(sql: String): Long =
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) }
        }

    /** PRAGMA foreign_key_list 第 3 列为父表名。 */
    private fun fkParents(table: String): List<String> =
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA foreign_key_list($table)").use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out += rs.getString(3)
                out
            }
        }

    private fun columns(table: String): List<String> =
        db.createStatement().use { st ->
            st.executeQuery("PRAGMA table_info($table)").use { rs ->
                val out = mutableListOf<String>()
                while (rs.next()) out += rs.getString(2)
                out
            }
        }

    private fun tableExists(table: String): Boolean =
        scalarLong("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table'") == 1L

    private fun queryRows(sql: String): List<List<String?>> =
        db.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                val n = rs.metaData.columnCount
                val out = mutableListOf<List<String?>>()
                while (rs.next()) out += (1..n).map { rs.getString(it) }
                out
            }
        }

    private fun createV1Schema(c: Connection) {
        c.createStatement().use { st ->
            st.execute(
                """
                CREATE TABLE books(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    author TEXT NOT NULL DEFAULT '',
                    filePath TEXT NOT NULL,
                    format TEXT NOT NULL,
                    coverPath TEXT,
                    fileSize INTEGER NOT NULL DEFAULT 0,
                    addedAt INTEGER NOT NULL,
                    lastReadAt INTEGER
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE reading_progress(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bookId INTEGER NOT NULL,
                    chapterIndex INTEGER NOT NULL DEFAULT 0,
                    charOffset INTEGER NOT NULL DEFAULT 0,
                    percentage REAL NOT NULL DEFAULT 0.0,
                    updatedAt INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE bookmarks(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bookId INTEGER NOT NULL,
                    chapterIndex INTEGER NOT NULL DEFAULT 0,
                    charOffset INTEGER NOT NULL DEFAULT 0,
                    name TEXT NOT NULL DEFAULT '',
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE highlights(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bookId INTEGER NOT NULL,
                    chapterIndex INTEGER NOT NULL DEFAULT 0,
                    startOffset INTEGER NOT NULL,
                    endOffset INTEGER NOT NULL,
                    color INTEGER NOT NULL,
                    note TEXT,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            st.execute(
                """
                CREATE TABLE notes(
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    bookId INTEGER NOT NULL,
                    highlightId INTEGER,
                    chapterIndex INTEGER NOT NULL DEFAULT 0,
                    charOffset INTEGER NOT NULL DEFAULT 0,
                    content TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    FOREIGN KEY(bookId) REFERENCES books(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(highlightId) REFERENCES highlights(id) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
        }
    }

    private fun insertV1Data(c: Connection) {
        c.createStatement().use { st ->
            st.execute("INSERT INTO books(id,title,author,filePath,format,fileSize,addedAt) VALUES (1,'Book One','A','/p1','EPUB',100,1111)")
            st.execute("INSERT INTO books(id,title,author,filePath,format,fileSize,addedAt) VALUES (2,'Book Two','B','/p2','TXT',200,2222)")
            st.execute("INSERT INTO reading_progress(id,bookId,chapterIndex,charOffset,percentage,updatedAt) VALUES (1,1,2,50,0.25,5555)")
            st.execute("INSERT INTO reading_progress(id,bookId,chapterIndex,charOffset,percentage,updatedAt) VALUES (2,2,0,0,0.0,6666)")
            st.execute("INSERT INTO bookmarks(id,bookId,chapterIndex,charOffset,name,createdAt) VALUES (1,1,1,10,'bm1',7000)")
            st.execute("INSERT INTO bookmarks(id,bookId,chapterIndex,charOffset,name,createdAt) VALUES (2,2,3,30,'bm2',8000)")
            st.execute("INSERT INTO highlights(id,bookId,chapterIndex,startOffset,endOffset,color,note,createdAt) VALUES (10,1,1,5,20,255,'hl1',9000)")
            st.execute("INSERT INTO highlights(id,bookId,chapterIndex,startOffset,endOffset,color,note,createdAt) VALUES (20,2,2,1,2,255,NULL,9100)")
            st.execute("INSERT INTO notes(id,bookId,highlightId,chapterIndex,charOffset,content,createdAt) VALUES (100,1,10,1,5,'linked',9999)")
            st.execute("INSERT INTO notes(id,bookId,highlightId,chapterIndex,charOffset,content,createdAt) VALUES (101,2,NULL,2,6,'orphan',10000)")
        }
    }
}
