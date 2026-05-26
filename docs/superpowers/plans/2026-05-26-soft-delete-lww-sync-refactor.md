# 软删除 + LWW 同步重构实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将同步机制从物理删除 + 手动冲突解决重构为软删除 + 自动 Last-Write-Wins 合并。

**架构：** 所有实体使用 uuid (String) 作为主键，增加 updatedAt (毫秒时间戳) 和 isDeleted 字段。同步时按 updatedAt 对比，保留最新记录。阅读进度取百分比最高。合并操作记录到 SyncLog 供用户查看。

**技术栈：** Kotlin, Room 2.7, Hilt 2.54, Jetpack Compose, Google Drive API, MockK + coroutines-test

---

## 文件结构

### 创建
- `app/src/main/java/.../domain/model/SyncLog.kt` — 合并日志领域模型
- `app/src/main/java/.../data/local/entity/SyncLogEntity.kt` — 合并日志实体
- `app/src/main/java/.../data/local/dao/SyncLogDao.kt` — 合并日志 DAO
- `app/src/test/java/.../domain/service/SyncServiceTest.kt` — 同步合并逻辑单元测试

### 修改
- 所有 domain model (Book, Bookmark, Highlight, Note, ReadingProgress)
- 所有 entity (BookEntity, BookmarkEntity, HighlightEntity, NoteEntity, ReadingProgressEntity)
- 所有 DAO (BookDao, BookmarkDao, HighlightDao, NoteDao, ReadingProgressDao)
- `SimpleBookDatabase.kt` — 迁移 v2→v3
- 所有 repository (BookRepository, BookmarkRepository, HighlightRepository, NoteRepository, ReadingProgressRepository)
- `SyncMetadata.kt` — 远端元数据增加 uuid/isDeleted/updatedAt
- `SyncService.kt` — 核心重写
- 所有 domain service (BookService, BookmarkService, NoteService, HighlightService, ReadingService)
- `Screen.kt`, `SimpleBookNavHost.kt` — 导航参数 Long→String
- 所有 ViewModel (ReaderViewModel, BookmarkViewModel, NoteViewModel, CollectionViewModel, SyncViewModel)
- 所有 Screen (ReaderScreen, BookmarkScreen, NoteScreen, CollectionScreen, BookListScreen)
- `DatabaseModule.kt` — 替换 ConflictDao→SyncLogDao
- `app/build.gradle.kts` — 添加测试依赖

### 删除（被替换）
- `ConflictRecordEntity.kt` → `SyncLogEntity.kt`
- `ConflictDao.kt` → `SyncLogDao.kt`
- `SyncScreen.kt` → 重写为合并日志展示

---

### 任务 1：添加测试依赖

**文件：**
- 修改：`app/build.gradle.kts:119-126`

- [ ] **步骤 1：添加 MockK 和 coroutines-test 依赖**

在 `app/build.gradle.kts` 的 `dependencies` 块中，替换 testing 部分：

```kotlin
    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.google.code.gson:gson:2.12.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
```

- [ ] **步骤 2：Sync 同步项目**

运行：`./gradlew assembleDebug --dry-run`
预期：BUILD SUCCESSFUL（验证依赖解析）

- [ ] **步骤 3：Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add MockK and coroutines-test for sync unit tests"
```

---

### 任务 2：更新 Domain Models

**文件：**
- 修改：`app/src/main/java/.../domain/model/Book.kt`
- 修改：`app/src/main/java/.../domain/model/Bookmark.kt`
- 修改：`app/src/main/java/.../domain/model/Highlight.kt`
- 修改：`app/src/main/java/.../domain/model/Note.kt`
- 修改：`app/src/main/java/.../domain/model/ReadingProgress.kt`
- 创建：`app/src/main/java/.../domain/model/SyncLog.kt`

- [ ] **步骤 1：更新 Book.kt**

将 `Book.kt` 替换为：

```kotlin
package com.ebookreader.simplebook.domain.model

data class Book(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val author: String = "",
    val filePath: String,
    val format: BookFormat,
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null
)
```

- [ ] **步骤 2：更新 Bookmark.kt**

```kotlin
package com.ebookreader.simplebook.domain.model

data class Bookmark(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val name: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 3：更新 Highlight.kt**

```kotlin
package com.ebookreader.simplebook.domain.model

data class Highlight(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val startOffset: Long,
    val endOffset: Long,
    val color: Long = 0xFFFFFF00,
    val note: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 4：更新 Note.kt**

```kotlin
package com.ebookreader.simplebook.domain.model

data class Note(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val highlightUuid: String? = null,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 5：更新 ReadingProgress.kt**

```kotlin
package com.ebookreader.simplebook.domain.model

data class ReadingProgress(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val percentage: Double = 0.0,
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 6：创建 SyncLog.kt**

创建 `app/src/main/java/com/ebookreader/simplebook/domain/model/SyncLog.kt`：

```kotlin
package com.ebookreader.simplebook.domain.model

data class SyncLog(
    val id: Long = 0,
    val entityType: String,
    val entityUuid: String,
    val action: String,
    val localUpdatedAt: Long?,
    val remoteUpdatedAt: Long?,
    val resolvedAt: Long = System.currentTimeMillis(),
    val bookUuid: String
)
```

- [ ] **步骤 7：编译验证**

运行：`./gradlew compileDebugKotlin`
预期：编译失败（因为下游代码仍引用旧字段名），这是预期行为。后续任务会逐步修复。记录当前错误数量作为基线。

- [ ] **步骤 8：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/model/
git commit -m "refactor: update domain models with uuid, updatedAt, isDeleted"
```

---

### 任务 3：更新 Entity 类

**文件：**
- 修改：`app/src/main/java/.../data/local/entity/BookEntity.kt`
- 修改：`app/src/main/java/.../data/local/entity/BookmarkEntity.kt`
- 修改：`app/src/main/java/.../data/local/entity/HighlightEntity.kt`
- 修改：`app/src/main/java/.../data/local/entity/NoteEntity.kt`
- 修改：`app/src/main/java/.../data/local/entity/ReadingProgressEntity.kt`
- 创建：`app/src/main/java/.../data/local/entity/SyncLogEntity.kt`
- 删除：`app/src/main/java/.../data/local/entity/ConflictRecordEntity.kt`

- [ ] **步骤 1：更新 BookEntity.kt**

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val uuid: String,
    val title: String,
    val author: String = "",
    val filePath: String,
    @ColumnInfo(name = "format") val format: String,
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long,
    val lastReadAt: Long? = null,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null
)
```

- [ ] **步骤 2：更新 BookmarkEntity.kt**

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "bookmarks",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class BookmarkEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val name: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 3：更新 HighlightEntity.kt**

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "highlights",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class HighlightEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int = 0xFFFFFF00.toInt(),
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 4：更新 NoteEntity.kt**

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["bookUuid"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = HighlightEntity::class,
            parentColumns = ["uuid"],
            childColumns = ["highlightUuid"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("bookUuid"), Index("highlightUuid")]
)
data class NoteEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val highlightUuid: String? = null,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 5：更新 ReadingProgressEntity.kt**

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "reading_progress",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class ReadingProgressEntity(
    @PrimaryKey val uuid: String,
    val bookUuid: String,
    val chapterIndex: Int = 0,
    val charOffset: Long = 0,
    val percentage: Double = 0.0,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 6：创建 SyncLogEntity.kt**

创建 `app/src/main/java/com/ebookreader/simplebook/data/local/entity/SyncLogEntity.kt`：

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(
    tableName = "sync_logs",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["uuid"],
        childColumns = ["bookUuid"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("bookUuid")]
)
data class SyncLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookUuid: String,
    val entityType: String,
    val entityUuid: String,
    val action: String,
    val localUpdatedAt: Long?,
    val remoteUpdatedAt: Long?,
    val resolvedAt: Long
)
```

- [ ] **步骤 7：删除 ConflictRecordEntity.kt**

```bash
rm app/src/main/java/com/ebookreader/simplebook/data/local/entity/ConflictRecordEntity.kt
```

- [ ] **步骤 8：Commit**

```bash
git add -A app/src/main/java/com/ebookreader/simplebook/data/local/entity/
git commit -m "refactor: update entities with uuid PK, soft delete; add SyncLogEntity"
```

---

### 任务 4：更新 DAOs

**文件：**
- 修改：`app/src/main/java/.../data/local/dao/BookDao.kt`
- 修改：`app/src/main/java/.../data/local/dao/BookmarkDao.kt`
- 修改：`app/src/main/java/.../data/local/dao/HighlightDao.kt`
- 修改：`app/src/main/java/.../data/local/dao/NoteDao.kt`
- 修改：`app/src/main/java/.../data/local/dao/ReadingProgressDao.kt`
- 创建：`app/src/main/java/.../data/local/dao/SyncLogDao.kt`
- 删除：`app/src/main/java/.../data/local/dao/ConflictDao.kt`

- [ ] **步骤 1：更新 BookDao.kt**

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebookreader.simplebook.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE uuid = :uuid")
    suspend fun getBookByUuid(uuid: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    suspend fun getAllBooksNow(): List<BookEntity>

    @Query("SELECT * FROM books WHERE isDeleted = 1")
    suspend fun getDeletedBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE driveFileId = :driveFileId")
    suspend fun getBookByDriveFileId(driveFileId: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAllBooksIncludingDeleted(): List<BookEntity>
}
```

- [ ] **步骤 2：更新 BookmarkDao.kt**

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getBookmarksForBook(bookUuid: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getBookmarksForBookNow(bookUuid: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid ORDER BY createdAt DESC")
    suspend fun getAllBookmarksForBookNow(bookUuid: String): List<BookmarkEntity>
}
```

- [ ] **步骤 3：更新 HighlightDao.kt**

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid AND chapterIndex = :chapterIndex AND isDeleted = 0")
    fun getHighlightsForChapter(bookUuid: String, chapterIndex: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getHighlightsForBook(bookUuid: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity)

    @Query("UPDATE highlights SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getHighlightsForBookNow(bookUuid: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid ORDER BY createdAt DESC")
    suspend fun getAllHighlightsForBookNow(bookUuid: String): List<HighlightEntity>
}
```

- [ ] **步骤 4：更新 NoteDao.kt**

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getNotesForBook(bookUuid: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getNotesForBookNow(bookUuid: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE bookUuid = :bookUuid ORDER BY createdAt DESC")
    suspend fun getAllNotesForBookNow(bookUuid: String): List<NoteEntity>
}
```

- [ ] **步骤 5：更新 ReadingProgressDao.kt**

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookUuid = :bookUuid AND isDeleted = 0")
    suspend fun getProgress(bookUuid: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookUuid = :bookUuid")
    suspend fun getProgressIncludingDeleted(bookUuid: String): ReadingProgressEntity?
}
```

- [ ] **步骤 6：创建 SyncLogDao.kt**

创建 `app/src/main/java/com/ebookreader/simplebook/data/local/dao/SyncLogDao.kt`：

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncLogDao {
    @Query("SELECT * FROM sync_logs ORDER BY resolvedAt DESC LIMIT 100")
    fun getRecentLogs(): Flow<List<SyncLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: SyncLogEntity)

    @Query("DELETE FROM sync_logs WHERE id NOT IN (SELECT id FROM sync_logs ORDER BY resolvedAt DESC LIMIT 100)")
    suspend fun pruneOldLogs()
}
```

- [ ] **步骤 7：删除 ConflictDao.kt**

```bash
rm app/src/main/java/com/ebookreader/simplebook/data/local/dao/ConflictDao.kt
```

- [ ] **步骤 8：Commit**

```bash
git add -A app/src/main/java/com/ebookreader/simplebook/data/local/dao/
git commit -m "refactor: update DAOs with soft-delete queries; add SyncLogDao"
```

---

### 任务 5：数据库迁移 v2 → v3

**文件：**
- 修改：`app/src/main/java/.../data/local/SimpleBookDatabase.kt`

- [ ] **步骤 1：重写 SimpleBookDatabase.kt**

```kotlin
package com.ebookreader.simplebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        SyncLogEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class SimpleBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun syncLogDao(): SyncLogDao

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
                // --- books ---
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
                       SELECT lower(hex(randomblob(4)) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))),2) || '-' || substr('89ab',abs(random()) % 4 + 1,1) || substr(lower(hex(randomblob(2))),2) || '-' || lower(hex(randomblob(6))), title, author, filePath, format, coverPath, fileSize, addedAt, lastReadAt, strftime('%s','now')*1000, 0, lastSyncedAt, driveFileId FROM books""".trimIndent()
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

                // --- notes ---
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
                // notes 需要先将 highlightId 映射到 highlight uuid
                db.execSQL(
                    """INSERT INTO notes_new (uuid, bookUuid, highlightUuid, chapterIndex, charOffset, content, createdAt, updatedAt, isDeleted)
                       SELECT lower(hex(randomblob(16))), b.uuid, h.uuid, n.chapterIndex, n.charOffset, n.content, n.createdAt, strftime('%s','now')*1000, 0
                       FROM notes n
                       JOIN books b ON n.bookId = b.uuid
                       LEFT JOIN highlights h ON n.highlightId = h.uuid""".trimIndent()
                )
                db.execSQL("DROP TABLE notes")
                db.execSQL("ALTER TABLE notes_new RENAME TO notes")

                // --- 删除旧 conflict_records，创建 sync_logs ---
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

                // --- 创建索引 ---
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bookmarks_bookUuid ON bookmarks(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_highlights_bookUuid ON highlights(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_bookUuid ON notes(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_highlightUuid ON notes(highlightUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_reading_progress_bookUuid ON reading_progress(bookUuid)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_logs_bookUuid ON sync_logs(bookUuid)")
            }
        }
    }
}
```

- [ ] **步骤 2：更新 DatabaseModule.kt**

将 `DatabaseModule.kt` 替换为：

```kotlin
package com.ebookreader.simplebook.di

import android.content.Context
import androidx.room.Room
import com.ebookreader.simplebook.data.local.SimpleBookDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SimpleBookDatabase =
        Room.databaseBuilder(
            context,
            SimpleBookDatabase::class.java,
            "simplebook.db"
        )
            .addMigrations(
                SimpleBookDatabase.MIGRATION_1_2,
                SimpleBookDatabase.MIGRATION_2_3
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(db: SimpleBookDatabase): BookDao = db.bookDao()

    @Provides
    fun provideReadingProgressDao(db: SimpleBookDatabase): ReadingProgressDao =
        db.readingProgressDao()

    @Provides
    fun provideBookmarkDao(db: SimpleBookDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideHighlightDao(db: SimpleBookDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideNoteDao(db: SimpleBookDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideSyncLogDao(db: SimpleBookDatabase): SyncLogDao = db.syncLogDao()
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/data/local/SimpleBookDatabase.kt app/src/main/java/com/ebookreader/simplebook/di/DatabaseModule.kt
git commit -m "refactor: add Room migration v2→v3 with uuid PK and soft delete"
```

---

### 任务 6：更新 Repositories

**文件：**
- 修改：`app/src/main/java/.../data/repository/BookRepository.kt`（接口）
- 修改：`app/src/main/java/.../data/repository/BookRepositoryImpl.kt`
- 修改：`app/src/main/java/.../data/repository/ReadingProgressRepository.kt`
- 修改：`app/src/main/java/.../data/repository/BookmarkRepository.kt`
- 修改：`app/src/main/java/.../data/repository/HighlightRepository.kt`
- 修改：`app/src/main/java/.../data/repository/NoteRepository.kt`

- [ ] **步骤 1：更新 BookRepository 接口**

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getAllBooksNow(): List<Book>
    suspend fun getBookByUuid(uuid: String): Book?
    suspend fun getBookByDriveFileId(driveFileId: String): Book?
    suspend fun addBook(book: Book): String
    suspend fun updateBook(book: Book)
    suspend fun softDeleteBook(uuid: String)
}
```

- [ ] **步骤 2：更新 BookRepositoryImpl**

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllBooksNow(): List<Book> =
        bookDao.getAllBooksNow().map { it.toDomain() }

    override suspend fun getBookByUuid(uuid: String): Book? =
        bookDao.getBookByUuid(uuid)?.toDomain()

    override suspend fun getBookByDriveFileId(driveFileId: String): Book? =
        bookDao.getBookByDriveFileId(driveFileId)?.toDomain()

    override suspend fun addBook(book: Book): String {
        bookDao.insert(book.toEntity())
        return book.uuid
    }

    override suspend fun updateBook(book: Book) =
        bookDao.update(book.toEntity())

    override suspend fun softDeleteBook(uuid: String) =
        bookDao.softDelete(uuid)

    private fun BookEntity.toDomain() = Book(
        uuid = uuid,
        title = title,
        author = author,
        filePath = filePath,
        format = BookFormat.valueOf(format),
        coverPath = coverPath,
        fileSize = fileSize,
        addedAt = addedAt,
        lastReadAt = lastReadAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        lastSyncedAt = lastSyncedAt,
        driveFileId = driveFileId
    )

    private fun Book.toEntity() = BookEntity(
        uuid = uuid,
        title = title,
        author = author,
        filePath = filePath,
        format = format.name,
        coverPath = coverPath,
        fileSize = fileSize,
        addedAt = addedAt,
        lastReadAt = lastReadAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted,
        lastSyncedAt = lastSyncedAt,
        driveFileId = driveFileId
    )
}
```

- [ ] **步骤 3：更新 ReadingProgressRepository**

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity
import com.ebookreader.simplebook.domain.model.ReadingProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingProgressRepository @Inject constructor(
    private val readingProgressDao: ReadingProgressDao
) {
    suspend fun getProgress(bookUuid: String): ReadingProgress? =
        readingProgressDao.getProgress(bookUuid)?.toDomain()

    suspend fun getProgressIncludingDeleted(bookUuid: String): ReadingProgress? =
        readingProgressDao.getProgressIncludingDeleted(bookUuid)?.toDomain()

    suspend fun saveProgress(progress: ReadingProgress) {
        readingProgressDao.upsert(progress.toEntity())
    }

    private fun ReadingProgressEntity.toDomain() = ReadingProgress(
        uuid = uuid,
        bookUuid = bookUuid,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        percentage = percentage,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun ReadingProgress.toEntity() = ReadingProgressEntity(
        uuid = uuid,
        bookUuid = bookUuid,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        percentage = percentage,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}
```

- [ ] **步骤 4：更新 BookmarkRepository**

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import com.ebookreader.simplebook.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao
) {
    fun getBookmarksForBook(bookUuid: String): Flow<List<Bookmark>> =
        bookmarkDao.getBookmarksForBook(bookUuid).map { list -> list.map { it.toDomain() } }

    suspend fun getBookmarksForBookNow(bookUuid: String): List<Bookmark> =
        bookmarkDao.getBookmarksForBookNow(bookUuid).map { it.toDomain() }

    suspend fun getAllBookmarksForBookNow(bookUuid: String): List<Bookmark> =
        bookmarkDao.getAllBookmarksForBookNow(bookUuid).map { it.toDomain() }

    fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.getAllBookmarks().map { list -> list.map { it.toDomain() } }

    suspend fun addBookmark(bookmark: Bookmark) {
        bookmarkDao.insert(bookmark.toEntity())
    }

    suspend fun softDeleteBookmark(uuid: String) {
        bookmarkDao.softDelete(uuid)
    }

    private fun BookmarkEntity.toDomain() = Bookmark(
        uuid = uuid,
        bookUuid = bookUuid,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun Bookmark.toEntity() = BookmarkEntity(
        uuid = uuid,
        bookUuid = bookUuid,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        name = name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}
```

- [ ] **步骤 5：更新 HighlightRepository**

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import com.ebookreader.simplebook.domain.model.Highlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val highlightDao: HighlightDao
) {
    fun getHighlightsForChapter(bookUuid: String, chapterIndex: Int): Flow<List<Highlight>> =
        highlightDao.getHighlightsForChapter(bookUuid, chapterIndex).map { list -> list.map { it.toDomain() } }

    fun getHighlightsForBook(bookUuid: String): Flow<List<Highlight>> =
        highlightDao.getHighlightsForBook(bookUuid).map { list -> list.map { it.toDomain() } }

    suspend fun getHighlightsForBookNow(bookUuid: String): List<Highlight> =
        highlightDao.getHighlightsForBookNow(bookUuid).map { it.toDomain() }

    suspend fun getAllHighlightsForBookNow(bookUuid: String): List<Highlight> =
        highlightDao.getAllHighlightsForBookNow(bookUuid).map { it.toDomain() }

    suspend fun addHighlight(highlight: Highlight) {
        highlightDao.insert(highlight.toEntity())
    }

    suspend fun softDeleteHighlight(uuid: String) {
        highlightDao.softDelete(uuid)
    }

    private fun HighlightEntity.toDomain() = Highlight(
        uuid = uuid,
        bookUuid = bookUuid,
        chapterIndex = chapterIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        color = color.toLong(),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun Highlight.toEntity() = HighlightEntity(
        uuid = uuid,
        bookUuid = bookUuid,
        chapterIndex = chapterIndex,
        startOffset = startOffset,
        endOffset = endOffset,
        color = color.toInt(),
        note = note,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}
```

- [ ] **步骤 6：更新 NoteRepository**

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun getNotesForBook(bookUuid: String): Flow<List<Note>> =
        noteDao.getNotesForBook(bookUuid).map { list -> list.map { it.toDomain() } }

    suspend fun getNotesForBookNow(bookUuid: String): List<Note> =
        noteDao.getNotesForBookNow(bookUuid).map { it.toDomain() }

    suspend fun getAllNotesForBookNow(bookUuid: String): List<Note> =
        noteDao.getAllNotesForBookNow(bookUuid).map { it.toDomain() }

    fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes().map { list -> list.map { it.toDomain() } }

    suspend fun addNote(note: Note) {
        noteDao.insert(note.toEntity())
    }

    suspend fun softDeleteNote(uuid: String) {
        noteDao.softDelete(uuid)
    }

    private fun NoteEntity.toDomain() = Note(
        uuid = uuid,
        bookUuid = bookUuid,
        highlightUuid = highlightUuid,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )

    private fun Note.toEntity() = NoteEntity(
        uuid = uuid,
        bookUuid = bookUuid,
        highlightUuid = highlightUuid,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        content = content,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isDeleted = isDeleted
    )
}
```

- [ ] **步骤 7：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/data/repository/
git commit -m "refactor: update repositories with uuid keys and soft delete"
```

---

### 任务 7：更新 SyncMetadata（远端数据模型）

**文件：**
- 修改：`app/src/main/java/.../data/remote/SyncMetadata.kt`

- [ ] **步骤 1：重写 SyncMetadata.kt**

```kotlin
package com.ebookreader.simplebook.data.remote

data class BookMetadata(
    val version: Int = 3,
    val bookUuid: String,
    val title: String,
    val author: String,
    val format: String,
    val fileSize: Long = 0,
    val coverPath: String? = null,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val progress: ProgressMetadata? = null,
    val bookmarks: List<BookmarkMetadata> = emptyList(),
    val highlights: List<HighlightMetadata> = emptyList(),
    val notes: List<NoteMetadata> = emptyList()
)

data class ProgressMetadata(
    val uuid: String,
    val chapterIndex: Int,
    val charOffset: Long,
    val percentage: Double,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class BookmarkMetadata(
    val uuid: String,
    val chapterIndex: Int,
    val charOffset: Long,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class HighlightMetadata(
    val uuid: String,
    val chapterIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)

data class NoteMetadata(
    val uuid: String,
    val highlightUuid: String? = null,
    val chapterIndex: Int,
    val charOffset: Long,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false
)
```

- [ ] **步骤 2：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/data/remote/SyncMetadata.kt
git commit -m "refactor: update SyncMetadata with uuid, isDeleted, updatedAt"
```

---

### 任务 8：编写 SyncService 合并逻辑测试（TDD）

**文件：**
- 创建：`app/src/test/java/com/ebookreader/simplebook/domain/service/SyncServiceTest.kt`

- [ ] **步骤 1：创建测试文件目录**

```bash
mkdir -p app/src/test/java/com/ebookreader/simplebook/domain/service
```

- [ ] **步骤 2：编写 SyncServiceTest.kt**

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.remote.BookMetadata
import com.ebookreader.simplebook.data.remote.BookmarkMetadata
import com.ebookreader.simplebook.data.remote.HighlightMetadata
import com.ebookreader.simplebook.data.remote.NoteMetadata
import com.ebookreader.simplebook.data.remote.ProgressMetadata
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Highlight
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.ReadingProgress
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncServiceTest {

    private lateinit var syncService: SyncService

    private val bookRepo = mockk<BookRepository>(relaxed = true)
    private val progressRepo = mockk<ReadingProgressRepository>(relaxed = true)
    private val bookmarkRepo = mockk<BookmarkRepository>(relaxed = true)
    private val highlightRepo = mockk<HighlightRepository>(relaxed = true)
    private val noteRepo = mockk<NoteRepository>(relaxed = true)
    private val syncLogDao = mockk<SyncLogDao>(relaxed = true)
    private val gson = Gson()

    @Before
    fun setup() {
        syncService = SyncService(
            context = mockk(relaxed = true),
            driveClient = mockk(relaxed = true),
            authManager = mockk(relaxed = true),
            bookRepository = bookRepo,
            readingProgressRepository = progressRepo,
            bookmarkRepository = bookmarkRepo,
            highlightRepository = highlightRepo,
            noteRepository = noteRepo,
            syncLogDao = syncLogDao,
            gson = gson
        )
    }

    // --- LWW merge tests ---

    @Test
    fun `mergeBookmark - remote newer, apply remote`() = runTest {
        val bookUuid = "book-1"
        val bmUuid = "bm-1"
        val local = Bookmark(uuid = bmUuid, bookUuid = bookUuid, name = "old", updatedAt = 1000)
        val remote = BookmarkMetadata(uuid = bmUuid, chapterIndex = 0, charOffset = 0, name = "new", createdAt = 1000, updatedAt = 2000)

        coEvery { bookmarkRepo.getAllBookmarksForBookNow(bookUuid) } returns listOf(local)

        syncService.mergeAnnotation(bookUuid, "bookmark", bmUuid, local, remote.updatedAt, remote.isDeleted) { repo ->
            repo.mergeBookmark(local, remote)
        }

        coVerify { bookmarkRepo.addBookmark(match { it.name == "new" && it.updatedAt == 2000L }) }
    }

    @Test
    fun `mergeBookmark - local newer, keep local`() = runTest {
        val bookUuid = "book-1"
        val bmUuid = "bm-1"
        val local = Bookmark(uuid = bmUuid, bookUuid = bookUuid, name = "local", updatedAt = 3000)
        val remote = BookmarkMetadata(uuid = bmUuid, chapterIndex = 0, charOffset = 0, name = "remote", createdAt = 1000, updatedAt = 2000)

        coEvery { bookmarkRepo.getAllBookmarksForBookNow(bookUuid) } returns listOf(local)

        syncService.mergeAnnotation(bookUuid, "bookmark", bmUuid, local, remote.updatedAt, remote.isDeleted) { repo ->
            repo.mergeBookmark(local, remote)
        }

        // Should NOT have called addBookmark since local wins
        coVerify(exactly = 0) { bookmarkRepo.addBookmark(any()) }
    }

    @Test
    fun `mergeProgress - remote has higher percentage, apply remote`() = runTest {
        val bookUuid = "book-1"
        val local = ReadingProgress(uuid = "prog-1", bookUuid = bookUuid, percentage = 30.0, updatedAt = 1000)
        val remote = ProgressMetadata(uuid = "prog-1", chapterIndex = 5, charOffset = 0, percentage = 60.0, updatedAt = 2000)

        coEvery { progressRepo.getProgressIncludingDeleted(bookUuid) } returns local

        syncService.mergeProgress(bookUuid, remote)

        coVerify { progressRepo.saveProgress(match { it.percentage == 60.0 }) }
    }

    @Test
    fun `mergeProgress - local has higher percentage, keep local`() = runTest {
        val bookUuid = "book-1"
        val local = ReadingProgress(uuid = "prog-1", bookUuid = bookUuid, percentage = 80.0, updatedAt = 1000)
        val remote = ProgressMetadata(uuid = "prog-1", chapterIndex = 2, charOffset = 0, percentage = 40.0, updatedAt = 2000)

        coEvery { progressRepo.getProgressIncludingDeleted(bookUuid) } returns local

        syncService.mergeProgress(bookUuid, remote)

        coVerify(exactly = 0) { progressRepo.saveProgress(any()) }
    }

    @Test
    fun `mergeProgress - remote soft deleted, local not deleted, keep local`() = runTest {
        val bookUuid = "book-1"
        val local = ReadingProgress(uuid = "prog-1", bookUuid = bookUuid, percentage = 50.0, updatedAt = 1000, isDeleted = false)
        val remote = ProgressMetadata(uuid = "prog-1", chapterIndex = 5, charOffset = 0, percentage = 50.0, updatedAt = 2000, isDeleted = true)

        coEvery { progressRepo.getProgressIncludingDeleted(bookUuid) } returns local

        syncService.mergeProgress(bookUuid, remote)

        coVerify(exactly = 0) { progressRepo.saveProgress(any()) }
    }

    @Test
    fun `soft deleted item from remote is applied to local`() = runTest {
        val bookUuid = "book-1"
        val bmUuid = "bm-1"
        val local = Bookmark(uuid = bmUuid, bookUuid = bookUuid, name = "exists", updatedAt = 1000, isDeleted = false)
        val remote = BookmarkMetadata(uuid = bmUuid, chapterIndex = 0, charOffset = 0, name = "exists", createdAt = 1000, updatedAt = 2000, isDeleted = true)

        coEvery { bookmarkRepo.getAllBookmarksForBookNow(bookUuid) } returns listOf(local)

        syncService.mergeAnnotation(bookUuid, "bookmark", bmUuid, local, remote.updatedAt, remote.isDeleted) { repo ->
            repo.mergeBookmark(local, remote)
        }

        coVerify { bookmarkRepo.addBookmark(match { it.isDeleted && it.uuid == bmUuid }) }
    }

    @Test
    fun `remote bookmark not in local is inserted`() = runTest {
        val bookUuid = "book-1"
        val remoteBm = BookmarkMetadata(uuid = "bm-new", chapterIndex = 3, charOffset = 100, name = "new bookmark", createdAt = 2000, updatedAt = 2000)

        coEvery { bookmarkRepo.getAllBookmarksForBookNow(bookUuid) } returns emptyList()

        syncService.mergeNewAnnotation(bookUuid, remoteBm)

        coVerify { bookmarkRepo.addBookmark(match { it.uuid == "bm-new" }) }
    }
}
```

- [ ] **步骤 3：运行测试确认失败**

运行：`./gradlew test --tests "com.ebookreader.simplebook.domain.service.SyncServiceTest"`
预期：COMPILATION ERROR — SyncService 的 mergeAnnotation/mergeProgress 方法尚未实现

- [ ] **步骤 4：Commit 测试文件**

```bash
git add app/src/test/
git commit -m "test: add SyncService merge logic unit tests (TDD red phase)"
```

---

### 任务 9：重写 SyncService

**文件：**
- 修改：`app/src/main/java/.../domain/service/SyncService.kt`

- [ ] **步骤 1：重写 SyncService.kt**

```kotlin
package com.ebookreader.simplebook.domain.service

import android.content.Context
import android.util.Log
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.data.remote.BookMetadata
import com.ebookreader.simplebook.data.remote.BookmarkMetadata
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.data.remote.HighlightMetadata
import com.ebookreader.simplebook.data.remote.NoteMetadata
import com.ebookreader.simplebook.data.remote.ProgressMetadata
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Highlight
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.ReadingProgress
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Success : SyncStatus()
}

@Singleton
class SyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveClient: GoogleDriveClient,
    private val authManager: AuthManager,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val noteRepository: NoteRepository,
    private val syncLogDao: SyncLogDao,
    private val gson: Gson
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val syncMutex = Mutex()

    companion object {
        private const val TAG = "SyncService"
    }

    suspend fun syncAll() {
        if (!authManager.isSignedIn) {
            _syncStatus.value = SyncStatus.Error("Not signed in")
            return
        }
        syncMutex.withLock {
            try {
                _syncStatus.value = SyncStatus.Syncing
                pullFromRemote()
                pushToRemote()
                val now = System.currentTimeMillis()
                _lastSyncedAt.value = now
                _syncStatus.value = SyncStatus.Success
            } catch (e: Exception) {
                Log.e(TAG, "syncAll: failed", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    suspend fun pushToRemote() {
        val appFolderId = driveClient.getAppFolderId()
        val books = bookRepository.getAllBooksNow()

        for (book in books) {
            val bookFolderName = "book_${book.uuid}"
            val bookFolderId = driveClient.createFolder(bookFolderName, appFolderId)
                ?: throw Exception("Failed to create book folder")

            if (book.driveFileId == null) {
                val fileName = "${book.title}.${book.format.name.lowercase()}"
                val localFile = File(book.filePath)
                if (localFile.exists()) {
                    val mimeType = when (book.format) {
                        BookFormat.EPUB -> "application/epub+zip"
                        BookFormat.TXT -> "text/plain"
                    }
                    val fileId = driveClient.uploadBookFile(bookFolderId, fileName, localFile, mimeType)
                    if (fileId != null) {
                        bookRepository.updateBook(book.copy(driveFileId = fileId))
                    }
                }
            }

            // Include ALL data (including soft-deleted) for sync
            val progress = readingProgressRepository.getProgressIncludingDeleted(book.uuid)
            val bookmarks = bookmarkRepository.getAllBookmarksForBookNow(book.uuid)
            val highlights = highlightRepository.getAllHighlightsForBookNow(book.uuid)
            val notes = noteRepository.getAllNotesForBookNow(book.uuid)

            val metadata = buildBookMetadata(book, progress, bookmarks, highlights, notes)
            val metadataJson = gson.toJson(metadata)
            driveClient.uploadFile(
                bookFolderId,
                "metadata.json",
                metadataJson.toByteArray(),
                "application/json"
            )

            bookRepository.updateBook(book.copy(lastSyncedAt = System.currentTimeMillis()))
        }
    }

    suspend fun pullFromRemote() {
        val appFolderId = driveClient.getAppFolderId()
        val remoteFolders = driveClient.listFilesInFolder(appFolderId)

        for ((folderName, folderId) in remoteFolders) {
            if (!folderName.startsWith("book_")) continue

            val metadataFileId = driveClient.findFileInFolder(folderId, "metadata.json") ?: continue
            val metadataBytes = driveClient.downloadFile(metadataFileId) ?: continue
            val metadataJson = String(metadataBytes)
            val metadata = try {
                gson.fromJson(metadataJson, BookMetadata::class.java)
            } catch (_: Exception) {
                continue
            }

            // Extract book uuid from folder name "book_{uuid}"
            val remoteBookUuid = folderName.removePrefix("book_")
            val localBook = bookRepository.getBookByUuid(remoteBookUuid)

            if (localBook == null) {
                if (!metadata.isDeleted) {
                    downloadBookFromRemote(folderId, folderName, metadata)
                }
            } else {
                mergeLocalBook(localBook, metadata, folderId)
            }
        }
    }

    // --- Internal merge methods (testable) ---

    internal suspend fun mergeProgress(bookUuid: String, remote: ProgressMetadata) {
        val local = readingProgressRepository.getProgressIncludingDeleted(bookUuid) ?: return
        val now = System.currentTimeMillis()

        if (remote.isDeleted && !local.isDeleted) {
            // Remote deleted but local active — keep local
            return
        }

        if (!remote.isDeleted && local.isDeleted) {
            // Local deleted but remote active — restore from remote
            readingProgressRepository.saveProgress(
                ReadingProgress(
                    uuid = remote.uuid,
                    bookUuid = bookUuid,
                    chapterIndex = remote.chapterIndex,
                    charOffset = remote.charOffset,
                    percentage = remote.percentage,
                    updatedAt = remote.updatedAt,
                    isDeleted = false
                )
            )
            recordLog(bookUuid, "progress", remote.uuid, "remote_won", local.updatedAt, remote.updatedAt)
            return
        }

        // Both active: take higher percentage
        if (remote.percentage > local.percentage) {
            readingProgressRepository.saveProgress(
                ReadingProgress(
                    uuid = remote.uuid,
                    bookUuid = bookUuid,
                    chapterIndex = remote.chapterIndex,
                    charOffset = remote.charOffset,
                    percentage = remote.percentage,
                    updatedAt = remote.updatedAt
                )
            )
            recordLog(bookUuid, "progress", remote.uuid, "remote_won", local.updatedAt, remote.updatedAt)
        }
    }

    internal suspend fun mergeAnnotation(
        bookUuid: String,
        entityType: String,
        entityUuid: String,
        local: Any?,
        remoteUpdatedAt: Long,
        remoteIsDeleted: Boolean,
        merger: suspend MergeContext.() -> Unit
    ) {
        val ctx = MergeContext(bookUuid, entityType, entityUuid, remoteUpdatedAt, remoteIsDeleted, local)
        ctx.merger()
    }

    internal suspend fun mergeBookmark(local: Bookmark, remote: BookmarkMetadata) {
        if (remote.updatedAt > local.updatedAt) {
            bookmarkRepository.addBookmark(
                Bookmark(
                    uuid = remote.uuid,
                    bookUuid = local.bookUuid,
                    chapterIndex = remote.chapterIndex,
                    charOffset = remote.charOffset,
                    name = remote.name,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    isDeleted = remote.isDeleted
                )
            )
            recordLog(local.bookUuid, "bookmark", remote.uuid, "remote_won", local.updatedAt, remote.updatedAt)
        }
    }

    internal suspend fun mergeHighlight(local: Highlight, remote: HighlightMetadata) {
        if (remote.updatedAt > local.updatedAt) {
            highlightRepository.addHighlight(
                Highlight(
                    uuid = remote.uuid,
                    bookUuid = local.bookUuid,
                    chapterIndex = remote.chapterIndex,
                    startOffset = remote.startOffset,
                    endOffset = remote.endOffset,
                    color = remote.color.toLong(),
                    note = remote.note,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    isDeleted = remote.isDeleted
                )
            )
            recordLog(local.bookUuid, "highlight", remote.uuid, "remote_won", local.updatedAt, remote.updatedAt)
        }
    }

    internal suspend fun mergeNote(local: Note, remote: NoteMetadata) {
        if (remote.updatedAt > local.updatedAt) {
            noteRepository.addNote(
                Note(
                    uuid = remote.uuid,
                    bookUuid = local.bookUuid,
                    highlightUuid = remote.highlightUuid,
                    chapterIndex = remote.chapterIndex,
                    charOffset = remote.charOffset,
                    content = remote.content,
                    createdAt = remote.createdAt,
                    updatedAt = remote.updatedAt,
                    isDeleted = remote.isDeleted
                )
            )
            recordLog(local.bookUuid, "note", remote.uuid, "remote_won", local.updatedAt, remote.updatedAt)
        }
    }

    internal suspend fun mergeNewAnnotation(bookUuid: String, remote: BookmarkMetadata) {
        bookmarkRepository.addBookmark(
            Bookmark(
                uuid = remote.uuid,
                bookUuid = bookUuid,
                chapterIndex = remote.chapterIndex,
                charOffset = remote.charOffset,
                name = remote.name,
                createdAt = remote.createdAt,
                updatedAt = remote.updatedAt,
                isDeleted = remote.isDeleted
            )
        )
    }

    // --- Private helpers ---

    private suspend fun mergeLocalBook(localBook: Book, metadata: BookMetadata, driveFolderId: String) {
        val now = System.currentTimeMillis()

        // Merge book itself via LWW
        if (metadata.updatedAt > localBook.updatedAt) {
            bookRepository.updateBook(
                localBook.copy(
                    title = metadata.title,
                    author = metadata.author,
                    updatedAt = metadata.updatedAt,
                    isDeleted = metadata.isDeleted,
                    driveFileId = driveFolderId
                )
            )
        }

        // Merge progress
        metadata.progress?.let { remoteProgress ->
            val localProgress = readingProgressRepository.getProgressIncludingDeleted(localBook.uuid)
            if (localProgress != null) {
                mergeProgress(localBook.uuid, remoteProgress)
            } else if (!remoteProgress.isDeleted) {
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        uuid = remoteProgress.uuid,
                        bookUuid = localBook.uuid,
                        chapterIndex = remoteProgress.chapterIndex,
                        charOffset = remoteProgress.charOffset,
                        percentage = remoteProgress.percentage,
                        updatedAt = remoteProgress.updatedAt
                    )
                )
            }
        }

        // Merge bookmarks
        val localBookmarks = bookmarkRepository.getAllBookmarksForBookNow(localBook.uuid)
        for (remoteBm in metadata.bookmarks) {
            val localMatch = localBookmarks.find { it.uuid == remoteBm.uuid }
            if (localMatch != null) {
                mergeBookmark(localMatch, remoteBm)
            } else if (!remoteBm.isDeleted) {
                bookmarkRepository.addBookmark(
                    Bookmark(
                        uuid = remoteBm.uuid,
                        bookUuid = localBook.uuid,
                        chapterIndex = remoteBm.chapterIndex,
                        charOffset = remoteBm.charOffset,
                        name = remoteBm.name,
                        createdAt = remoteBm.createdAt,
                        updatedAt = remoteBm.updatedAt
                    )
                )
            }
        }

        // Merge highlights
        val localHighlights = highlightRepository.getAllHighlightsForBookNow(localBook.uuid)
        for (remoteHl in metadata.highlights) {
            val localMatch = localHighlights.find { it.uuid == remoteHl.uuid }
            if (localMatch != null) {
                mergeHighlight(localMatch, remoteHl)
            } else if (!remoteHl.isDeleted) {
                highlightRepository.addHighlight(
                    Highlight(
                        uuid = remoteHl.uuid,
                        bookUuid = localBook.uuid,
                        chapterIndex = remoteHl.chapterIndex,
                        startOffset = remoteHl.startOffset,
                        endOffset = remoteHl.endOffset,
                        color = remoteHl.color.toLong(),
                        note = remoteHl.note,
                        createdAt = remoteHl.createdAt,
                        updatedAt = remoteHl.updatedAt
                    )
                )
            }
        }

        // Merge notes
        val localNotes = noteRepository.getAllNotesForBookNow(localBook.uuid)
        for (remoteNt in metadata.notes) {
            val localMatch = localNotes.find { it.uuid == remoteNt.uuid }
            if (localMatch != null) {
                mergeNote(localMatch, remoteNt)
            } else if (!remoteNt.isDeleted) {
                noteRepository.addNote(
                    Note(
                        uuid = remoteNt.uuid,
                        bookUuid = localBook.uuid,
                        highlightUuid = remoteNt.highlightUuid,
                        chapterIndex = remoteNt.chapterIndex,
                        charOffset = remoteNt.charOffset,
                        content = remoteNt.content,
                        createdAt = remoteNt.createdAt,
                        updatedAt = remoteNt.updatedAt
                    )
                )
            }
        }
    }

    private suspend fun downloadBookFromRemote(
        folderId: String,
        folderName: String,
        metadata: BookMetadata
    ) {
        val filesInFolder = driveClient.listFilesInFolder(folderId)
        val bookFileEntry = filesInFolder.firstOrNull { (name, _) ->
            !name.startsWith("metadata") && (name.endsWith(".epub") || name.endsWith(".txt"))
        } ?: return

        val extension = bookFileEntry.first.substringAfterLast('.').lowercase()
        val format = when (extension) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            else -> return
        }

        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val localFileName = "${UUID.randomUUID()}.$extension"
        val localFile = File(booksDir, localFileName)
        driveClient.downloadFileTo(bookFileEntry.second, localFile)
        if (!localFile.exists()) return

        val book = Book(
            uuid = metadata.bookUuid,
            title = metadata.title,
            author = metadata.author,
            filePath = localFile.absolutePath,
            format = format,
            fileSize = localFile.length(),
            updatedAt = metadata.updatedAt,
            lastSyncedAt = System.currentTimeMillis(),
            driveFileId = folderId
        )
        bookRepository.addBook(book)

        metadata.progress?.let { prog ->
            if (!prog.isDeleted) {
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        uuid = prog.uuid,
                        bookUuid = book.uuid,
                        chapterIndex = prog.chapterIndex,
                        charOffset = prog.charOffset,
                        percentage = prog.percentage,
                        updatedAt = prog.updatedAt
                    )
                )
            }
        }

        for (bm in metadata.bookmarks) {
            if (!bm.isDeleted) {
                bookmarkRepository.addBookmark(
                    Bookmark(
                        uuid = bm.uuid,
                        bookUuid = book.uuid,
                        chapterIndex = bm.chapterIndex,
                        charOffset = bm.charOffset,
                        name = bm.name,
                        createdAt = bm.createdAt,
                        updatedAt = bm.updatedAt
                    )
                )
            }
        }

        for (hl in metadata.highlights) {
            if (!hl.isDeleted) {
                highlightRepository.addHighlight(
                    Highlight(
                        uuid = hl.uuid,
                        bookUuid = book.uuid,
                        chapterIndex = hl.chapterIndex,
                        startOffset = hl.startOffset,
                        endOffset = hl.endOffset,
                        color = hl.color.toLong(),
                        note = hl.note,
                        createdAt = hl.createdAt,
                        updatedAt = hl.updatedAt
                    )
                )
            }
        }

        for (nt in metadata.notes) {
            if (!nt.isDeleted) {
                noteRepository.addNote(
                    Note(
                        uuid = nt.uuid,
                        bookUuid = book.uuid,
                        highlightUuid = nt.highlightUuid,
                        chapterIndex = nt.chapterIndex,
                        charOffset = nt.charOffset,
                        content = nt.content,
                        createdAt = nt.createdAt,
                        updatedAt = nt.updatedAt
                    )
                )
            }
        }
    }

    private fun buildBookMetadata(
        book: Book,
        progress: ReadingProgress?,
        bookmarks: List<Bookmark>,
        highlights: List<Highlight>,
        notes: List<Note>
    ): BookMetadata {
        return BookMetadata(
            version = 3,
            bookUuid = book.uuid,
            title = book.title,
            author = book.author,
            format = book.format.name,
            fileSize = book.fileSize,
            coverPath = book.coverPath,
            updatedAt = book.updatedAt,
            isDeleted = book.isDeleted,
            progress = progress?.let {
                ProgressMetadata(
                    uuid = it.uuid,
                    chapterIndex = it.chapterIndex,
                    charOffset = it.charOffset,
                    percentage = it.percentage,
                    updatedAt = it.updatedAt,
                    isDeleted = it.isDeleted
                )
            },
            bookmarks = bookmarks.map { bm ->
                BookmarkMetadata(
                    uuid = bm.uuid,
                    chapterIndex = bm.chapterIndex,
                    charOffset = bm.charOffset,
                    name = bm.name,
                    createdAt = bm.createdAt,
                    updatedAt = bm.updatedAt,
                    isDeleted = bm.isDeleted
                )
            },
            highlights = highlights.map { hl ->
                HighlightMetadata(
                    uuid = hl.uuid,
                    chapterIndex = hl.chapterIndex,
                    startOffset = hl.startOffset,
                    endOffset = hl.endOffset,
                    color = hl.color.toInt(),
                    note = hl.note,
                    createdAt = hl.createdAt,
                    updatedAt = hl.updatedAt,
                    isDeleted = hl.isDeleted
                )
            },
            notes = notes.map { nt ->
                NoteMetadata(
                    uuid = nt.uuid,
                    highlightUuid = nt.highlightUuid,
                    chapterIndex = nt.chapterIndex,
                    charOffset = nt.charOffset,
                    content = nt.content,
                    createdAt = nt.createdAt,
                    updatedAt = nt.updatedAt,
                    isDeleted = nt.isDeleted
                )
            }
        )
    }

    private suspend fun recordLog(
        bookUuid: String,
        entityType: String,
        entityUuid: String,
        action: String,
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long?
    ) {
        syncLogDao.insert(
            SyncLogEntity(
                bookUuid = bookUuid,
                entityType = entityType,
                entityUuid = entityUuid,
                action = action,
                localUpdatedAt = localUpdatedAt,
                remoteUpdatedAt = remoteUpdatedAt,
                resolvedAt = System.currentTimeMillis()
            )
        )
        syncLogDao.pruneOldLogs()
    }
}

data class MergeContext(
    val bookUuid: String,
    val entityType: String,
    val entityUuid: String,
    val remoteUpdatedAt: Long,
    val remoteIsDeleted: Boolean,
    val local: Any?
)
```

- [ ] **步骤 2：运行测试验证通过**

运行：`./gradlew test --tests "com.ebookreader.simplebook.domain.service.SyncServiceTest"`
预期：所有 7 个测试通过

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt
git commit -m "feat: rewrite SyncService with LWW merge and soft-delete support"
```

---

### 任务 10：更新 Domain Services

**文件：**
- 修改：`app/src/main/java/.../domain/service/BookService.kt`
- 修改：`app/src/main/java/.../domain/service/BookmarkService.kt`
- 修改：`app/src/main/java/.../domain/service/HighlightService.kt`
- 修改：`app/src/main/java/.../domain/service/NoteService.kt`
- 修改：`app/src/main/java/.../domain/service/ReadingService.kt`

- [ ] **步骤 1：更新 BookService.kt**

关键变更：`getBookById(Long)` → `getBookByUuid(String)`，`deleteBook` → `softDeleteBook`，`addBook` 返回 `String`

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookService @Inject constructor(
    private val bookRepository: BookRepository,
    private val epubParser: EpubParser,
    private val txtParser: TxtParser
) {
    fun getAllBooks(): Flow<List<Book>> = bookRepository.getAllBooks()

    suspend fun getBookByUuid(uuid: String): Book? = bookRepository.getBookByUuid(uuid)

    suspend fun importBook(file: File, originalName: String = file.nameWithoutExtension): Book {
        val format = when (file.extension.lowercase()) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            else -> throw IllegalArgumentException("Unsupported format: ${file.extension}")
        }

        val title: String
        val author: String
        var coverPath: String? = null

        when (format) {
            BookFormat.EPUB -> {
                val result = epubParser.parse(file)
                title = result.title.ifBlank { originalName }
                author = result.author
                coverPath = result.coverPath
            }
            BookFormat.TXT -> {
                val result = txtParser.parse(file)
                title = originalName
                author = result.author
            }
        }

        val book = Book(
            title = title,
            author = author,
            filePath = file.absolutePath,
            format = format,
            coverPath = coverPath,
            fileSize = file.length()
        )

        bookRepository.addBook(book)
        return book
    }

    suspend fun softDeleteBook(uuid: String) = bookRepository.softDeleteBook(uuid)
}
```

- [ ] **步骤 2：更新 BookmarkService.kt**

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkService @Inject constructor(
    private val bookmarkRepo: BookmarkRepository
) {
    fun getBookmarksForBook(bookUuid: String): Flow<List<Bookmark>> =
        bookmarkRepo.getBookmarksForBook(bookUuid)

    fun getAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkRepo.getAllBookmarks()

    suspend fun addBookmark(bookUuid: String, chapterIndex: Int, charOffset: Long, name: String) {
        bookmarkRepo.addBookmark(
            Bookmark(
                bookUuid = bookUuid,
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                name = name
            )
        )
    }

    suspend fun deleteBookmarkForPosition(bookUuid: String, chapterIndex: Int) {
        val bookmarks = bookmarkRepo.getBookmarksForBook(bookUuid).first()
        val match = bookmarks.find { it.chapterIndex == chapterIndex }
        if (match != null) {
            bookmarkRepo.softDeleteBookmark(match.uuid)
        }
    }

    suspend fun softDeleteBookmark(bookmark: Bookmark) {
        bookmarkRepo.softDeleteBookmark(bookmark.uuid)
    }

    suspend fun isBookmarked(bookUuid: String, chapterIndex: Int): Boolean {
        val bookmarks = bookmarkRepo.getBookmarksForBook(bookUuid).first()
        return bookmarks.any { it.chapterIndex == chapterIndex }
    }
}
```

- [ ] **步骤 3：更新 HighlightService.kt**

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.domain.model.Highlight
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightService @Inject constructor(
    private val highlightRepo: HighlightRepository
) {
    fun getHighlightsForChapter(bookUuid: String, chapterIndex: Int): Flow<List<Highlight>> =
        highlightRepo.getHighlightsForChapter(bookUuid, chapterIndex)

    fun getHighlightsForBook(bookUuid: String): Flow<List<Highlight>> =
        highlightRepo.getHighlightsForBook(bookUuid)

    suspend fun addHighlight(highlight: Highlight) =
        highlightRepo.addHighlight(highlight)

    suspend fun softDeleteHighlight(highlight: Highlight) =
        highlightRepo.softDeleteHighlight(highlight.uuid)
}
```

- [ ] **步骤 4：更新 NoteService.kt**

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.domain.model.Note
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteService @Inject constructor(
    private val noteRepo: NoteRepository
) {
    fun getNotesForBook(bookUuid: String): Flow<List<Note>> = noteRepo.getNotesForBook(bookUuid)
    fun getAllNotes(): Flow<List<Note>> = noteRepo.getAllNotes()
    suspend fun addNote(note: Note) = noteRepo.addNote(note)
    suspend fun softDeleteNote(note: Note) = noteRepo.softDeleteNote(note.uuid)
}
```

- [ ] **步骤 5：更新 ReadingService.kt**

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.ReadingProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReadingService @Inject constructor(
    private val readingProgressRepo: ReadingProgressRepository,
    private val bookRepo: BookRepository
) {
    suspend fun saveProgress(bookUuid: String, chapterIndex: Int, charOffset: Long, percentage: Double) {
        val existing = readingProgressRepo.getProgress(bookUuid)
        val now = System.currentTimeMillis()
        val progress = if (existing != null) {
            existing.copy(
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                percentage = percentage,
                updatedAt = now
            )
        } else {
            ReadingProgress(
                bookUuid = bookUuid,
                chapterIndex = chapterIndex,
                charOffset = charOffset,
                percentage = percentage,
                updatedAt = now
            )
        }
        readingProgressRepo.saveProgress(progress)

        bookRepo.getBookByUuid(bookUuid)?.let { book ->
            bookRepo.updateBook(book.copy(
                lastReadAt = now,
                updatedAt = now
            ))
        }
    }

    suspend fun loadProgress(bookUuid: String): ReadingProgress? =
        readingProgressRepo.getProgress(bookUuid)
}
```

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/service/BookService.kt \
       app/src/main/java/com/ebookreader/simplebook/domain/service/BookmarkService.kt \
       app/src/main/java/com/ebookreader/simplebook/domain/service/HighlightService.kt \
       app/src/main/java/com/ebookreader/simplebook/domain/service/NoteService.kt \
       app/src/main/java/com/ebookreader/simplebook/domain/service/ReadingService.kt
git commit -m "refactor: update domain services with uuid keys and soft delete"
```

---

### 任务 11：更新导航

**文件：**
- 修改：`app/src/main/java/.../ui/navigation/Screen.kt`
- 修改：`app/src/main/java/.../ui/navigation/SimpleBookNavHost.kt`

- [ ] **步骤 1：更新 Screen.kt**

```kotlin
package com.ebookreader.simplebook.ui.navigation

sealed class Screen(val route: String) {
    data object BookList : Screen("book_list")
    data object Collection : Screen("collection")
    data object Reader : Screen("reader/{bookUuid}?charOffset={charOffset}&chapterIndex={chapterIndex}") {
        fun createRoute(bookUuid: String, charOffset: Long = 0L, chapterIndex: Int = -1) =
            "reader/$bookUuid?charOffset=$charOffset&chapterIndex=$chapterIndex"
    }
    data object Bookmark : Screen("bookmark/{bookUuid}") {
        fun createRoute(bookUuid: String) = "bookmark/$bookUuid"
    }
    data object NoteList : Screen("notes/{bookUuid}") {
        fun createRoute(bookUuid: String) = "notes/$bookUuid"
    }
    data object Settings : Screen("settings")
    data object SyncLog : Screen("sync_log")
}
```

- [ ] **步骤 2：更新 SimpleBookNavHost.kt**

关键变更：`NavType.LongType` → `NavType.StringType`，`getLong` → `getString`，`book.id` → `book.uuid`

```kotlin
package com.ebookreader.simplebook.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.ui.collection.CollectionScreen
import com.ebookreader.simplebook.ui.booklist.BookListScreen
import com.ebookreader.simplebook.ui.bookmark.BookmarkScreen
import com.ebookreader.simplebook.ui.note.NoteScreen
import com.ebookreader.simplebook.ui.reader.ReaderScreen
import com.ebookreader.simplebook.ui.settings.SettingsScreen
import com.ebookreader.simplebook.ui.sync.SyncLogScreen
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun SimpleBookNavHost(
    navController: NavHostController,
    windowSizeClass: WindowSizeClass,
    syncViewModel: SyncViewModel? = null,
    signInLauncher: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.BookList.route,
        modifier = modifier
    ) {
        composable(Screen.Collection.route) {
            CollectionScreen(
                navController = navController,
                windowWidthSizeClass = windowSizeClass.widthSizeClass
            )
        }

        composable(Screen.BookList.route) {
            BookListScreen(
                windowWidthSizeClass = windowSizeClass.widthSizeClass,
                onBookClick = { book: Book ->
                    navController.navigate(Screen.Reader.createRoute(book.uuid))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookUuid") { type = NavType.StringType },
                navArgument("charOffset") {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument("chapterIndex") {
                    type = NavType.IntType
                    defaultValue = -1
                }
            )
        ) { backStackEntry ->
            val bookUuid = backStackEntry.arguments?.getString("bookUuid") ?: ""
            ReaderScreen(
                bookUuid = bookUuid,
                navController = navController,
                windowWidthSizeClass = windowSizeClass.widthSizeClass
            )
        }

        composable(
            route = Screen.Bookmark.route,
            arguments = listOf(
                navArgument("bookUuid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookUuid = backStackEntry.arguments?.getString("bookUuid") ?: ""
            BookmarkScreen(bookUuid = bookUuid)
        }

        composable(
            route = Screen.NoteList.route,
            arguments = listOf(
                navArgument("bookUuid") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookUuid = backStackEntry.arguments?.getString("bookUuid") ?: ""
            NoteScreen(bookUuid = bookUuid)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                navController = navController,
                onSignInClick = signInLauncher
            )
        }

        composable(Screen.SyncLog.route) {
            SyncLogScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
```

- [ ] **步骤 3：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/navigation/
git commit -m "refactor: update navigation from Long bookId to String bookUuid"
```

---

### 任务 12：更新 ViewModels

**文件：**
- 修改：`app/src/main/java/.../ui/reader/ReaderViewModel.kt`
- 修改：`app/src/main/java/.../ui/bookmark/BookmarkViewModel.kt`（如果存在）
- 修改：`app/src/main/java/.../ui/note/NoteViewModel.kt`（如果存在）
- 修改：`app/src/main/java/.../ui/collection/CollectionViewModel.kt`（如果存在）
- 修改：`app/src/main/java/.../ui/sync/SyncViewModel.kt`

此任务的核心变更模式：所有 `bookId: Long` → `bookUuid: String`，所有 `id: Long` 引用 → `uuid: String`。

- [ ] **步骤 1：更新 ReaderViewModel.kt**

变更点：
- 第 49 行：`private val bookId: Long` → `private val bookUuid: String = savedStateHandle["bookUuid"] ?: ""`
- 所有 `bookId` 引用 → `bookUuid`
- `bookService.getBookById(bookId)` → `bookService.getBookByUuid(bookUuid)`
- `readingService.loadProgress(bookId)` → `readingService.loadProgress(bookUuid)`
- `readingService.saveProgress(bookId = bookId, ...)` → `readingService.saveProgress(bookUuid = bookUuid, ...)`
- `bookmarkService.getBookmarksForBook(bookId)` → `bookmarkService.getBookmarksForBook(bookUuid)`
- `bookmarkService.addBookmark(bookId, ...)` → `bookmarkService.addBookmark(bookUuid, ...)`
- `bookmarkService.deleteBookmarkForPosition(bookId, ...)` → `bookmarkService.deleteBookmarkForPosition(bookUuid, ...)`
- `bookmarkService.isBookmarked(bookId, ...)` → `bookmarkService.isBookmarked(bookUuid, ...)`
- `noteService.getNotesForBook(bookId)` → `noteService.getNotesForBook(bookUuid)`
- `Note(bookId = bookId, ...)` → `Note(bookUuid = bookUuid, ...)`

- [ ] **步骤 2：更新 BookmarkViewModel.kt**

变更点：
- `private val bookId: Long` → `private val bookUuid: String = savedStateHandle["bookUuid"] ?: ""`
- 所有 `bookId` → `bookUuid`
- `bookmarkService.getBookmarksForBook(bookId)` → `bookmarkService.getBookmarksForBook(bookUuid)`

- [ ] **步骤 3：更新 NoteViewModel.kt**

变更点：
- `private val bookId: Long` → `private val bookUuid: String = savedStateHandle["bookUuid"] ?: ""`
- 所有 `bookId` → `bookUuid`
- `noteService.getNotesForBook(bookId)` → `noteService.getNotesForBook(bookUuid)`

- [ ] **步骤 4：更新 CollectionViewModel.kt**

变更点：
- `it.id` → `it.uuid`（在 `associateBy` 中）
- `it.bookId` → `it.bookUuid`（在 `groupBy` 中）
- `bookMap[bookId]` → `bookMap[bookUuid]`
- `bookmarkService.deleteBookmark(bookmark)` → `bookmarkService.softDeleteBookmark(bookmark)`
- `noteService.deleteNote(note)` → `noteService.softDeleteNote(note)`
- 导航调用中的 `bookmark.bookId` → `bookmark.bookUuid`
- 导航调用中的 `note.bookId` → `note.bookUuid`

- [ ] **步骤 5：更新 SyncViewModel.kt**

替换为：

```kotlin
package com.ebookreader.simplebook.ui.sync

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncService: SyncService,
    val authManager: AuthManager,
    private val syncLogDao: SyncLogDao
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    val lastSyncedAt: StateFlow<Long?> = syncService.lastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val syncLogs: StateFlow<List<SyncLogEntity>> = syncLogDao.getRecentLogs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isSignedIn: Boolean get() = authManager.isSignedIn
    val accountEmail: String? get() = authManager.signedInAccount.value?.email

    fun getSignInIntent(): Intent = authManager.signInIntent

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError.asStateFlow()

    fun handleSignInResult(data: Intent) {
        viewModelScope.launch {
            val result = authManager.handleSignInResult(data)
            if (result.isFailure) {
                _signInError.value = result.exceptionOrNull()?.message ?: "Sign-in failed"
            } else {
                _signInError.value = null
            }
        }
    }

    fun clearSignInError() { _signInError.value = null }

    fun setSignInError(message: String) { _signInError.value = message }

    fun syncNow() {
        viewModelScope.launch { syncService.syncAll() }
    }

    fun signOut() { authManager.signOut() }
}
```

- [ ] **步骤 6：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/
git commit -m "refactor: update ViewModels from Long bookId to String bookUuid"
```

---

### 任务 13：更新 Screens

**文件：**
- 修改：`app/src/main/java/.../ui/reader/ReaderScreen.kt`
- 修改：`app/src/main/java/.../ui/bookmark/BookmarkScreen.kt`
- 修改：`app/src/main/java/.../ui/note/NoteScreen.kt`
- 修改：`app/src/main/java/.../ui/booklist/BookListScreen.kt`
- 修改：`app/src/main/java/.../ui/collection/CollectionScreen.kt`
- 修改：`app/src/main/java/.../ui/settings/SettingsScreen.kt`（Sync 导航入口改为 SyncLog）
- 重写：`app/src/main/java/.../ui/sync/SyncScreen.kt` → `SyncLogScreen.kt`

此任务的核心变更模式：所有 Screen 函数参数 `bookId: Long` → `bookUuid: String`。

- [ ] **步骤 1：更新 ReaderScreen.kt**

变更点：
- `fun ReaderScreen(bookId: Long, ...)` → `fun ReaderScreen(bookUuid: String, ...)`
- 内部 ViewModel 调用已通过 SavedStateHandle 自动适配（任务 12 已处理）

- [ ] **步骤 2：更新 BookmarkScreen.kt**

变更点：
- `fun BookmarkScreen(bookId: Long)` → `fun BookmarkScreen(bookUuid: String)`

- [ ] **步骤 3：更新 NoteScreen.kt**

变更点：
- `fun NoteScreen(bookId: Long)` → `fun NoteScreen(bookUuid: String)`

- [ ] **步骤 4：更新 BookListScreen.kt**

无需改动函数签名，仅确认 `onBookClick` 回调使用 `book.uuid`（已在任务 11 导航中处理）。

- [ ] **步骤 5：更新 CollectionScreen.kt**

变更点：
- 导航调用中 `Screen.Reader.createRoute(bookmark.bookId, ...)` → `Screen.Reader.createRoute(bookmark.bookUuid, ...)`
- 导航调用中 `Screen.Reader.createRoute(note.bookId, ...)` → `Screen.Reader.createRoute(note.bookUuid, ...)`

- [ ] **步骤 6：更新 SettingsScreen.kt**

变更点：
- Sync 导航 `Screen.Sync.route` → `Screen.SyncLog.route`

- [ ] **步骤 7：重写 SyncScreen.kt → SyncLogScreen.kt**

将 `SyncScreen.kt` 重写为合并日志展示：

```kotlin
package com.ebookreader.simplebook.ui.sync

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel()
) {
    val syncLogs by viewModel.syncLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("同步记录") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        if (syncLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("暂无同步记录", style = MaterialTheme.typography.titleLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(16.dp)
            ) {
                items(syncLogs, key = { it.id }) { log ->
                    SyncLogItem(log)
                }
            }
        }
    }
}

@Composable
private fun SyncLogItem(log: SyncLogEntity) {
    val typeLabel = when (log.entityType) {
        "progress" -> "阅读进度"
        "bookmark" -> "书签"
        "highlight" -> "高亮"
        "note" -> "笔记"
        else -> log.entityType
    }
    val actionLabel = when (log.action) {
        "remote_won" -> "远端覆盖"
        "local_won" -> "保留本地"
        else -> log.action
    }
    val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        .format(Date(log.resolvedAt))

    Column(
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Text(
            "$typeLabel · $actionLabel",
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            time,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
```

- [ ] **步骤 8：编译验证**

运行：`./gradlew compileDebugKotlin`
预期：BUILD SUCCESSFUL

- [ ] **步骤 9：运行测试确认通过**

运行：`./gradlew test --tests "com.ebookreader.simplebook.domain.service.SyncServiceTest"`
预期：所有测试通过

- [ ] **步骤 10：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/ui/
git commit -m "refactor: update screens from Long bookId to String bookUuid; add SyncLogScreen"
```

---

### 任务 14：更新 FileImportService（收尾）

**文件：**
- 修改：`app/src/main/java/.../domain/service/FileImportService.kt`

- [ ] **步骤 1：更新 FileImportService.kt**

变更点：
- `bookService.importBook` 现在直接返回 `Book`（不再返回 `id: Long`）
- `val id = bookRepository.addBook(book); return book.copy(id = id)` 模式已废弃
- 确保 `importBook` 返回值中 `book.uuid` 已正确设置

检查 `FileImportService.kt` 中的 `importedBooks.add(book)` 调用，确认 `book` 对象已包含 `uuid`（由 `BookService.importBook` 自动生成）。

- [ ] **步骤 2：最终编译验证**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：运行全部测试**

运行：`./gradlew test`
预期：所有测试通过

- [ ] **步骤 4：最终 Commit**

```bash
git add -A
git commit -m "refactor: finalize soft-delete + LWW sync refactor across all layers"
```

---

## 自检

**1. 规格覆盖度：**
| 规格需求 | 对应任务 |
|----------|---------|
| uuid 替代 Long id 成主键 | 任务 2-6 |
| updated_at 毫秒时间戳 | 任务 2-3 |
| is_deleted 软删除 | 任务 3-4 |
| 严禁物理删除 | 任务 4 (DAO 只保留 softDelete) |
| LWW 冲突解决 | 任务 8-9 (SyncService) |
| 阅读进度取百分比最高 | 任务 8-9 (mergeProgress) |
| 前端过滤 is_deleted | 任务 4 (DAO WHERE 条件) |
| 合并日志 UI | 任务 13 (SyncLogScreen) |
| metadata.json v3 格式 | 任务 7 |
| 数据库迁移 v2→v3 | 任务 5 |
| 100 条日志上限 | 任务 4 (SyncLogDao.pruneOldLogs) |

**2. 占位符扫描：** 无 TODO、TBD、"待定"、"后续实现"。所有步骤包含具体代码。

**3. 类型一致性：**
- 所有 `uuid` 字段类型为 `String`，全链路一致（Entity → Domain → Metadata → DAO → Repository → Service → ViewModel → Screen）
- 所有 `updatedAt` 字段类型为 `Long`，全链路一致
- 所有 `isDeleted` 字段类型为 `Boolean`（Entity 中 Room 用 `Int` 0/1，`toDomain()` 映射正确）
