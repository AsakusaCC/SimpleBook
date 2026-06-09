# SimpleBook v1.0 — Google Drive 同步 + 封面进度 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在 v0.5 本地阅读器基础上，新增 Google Drive 云同步（书籍文件 + 阅读进度 + 书签/高亮/笔记）和书架封面阅读进度百分比显示。

**架构：** 按书籍粒度同步到 Google Drive App Folder，每本书一个子文件夹含书籍文件 + metadata.json。版本号追踪冲突，冲突时弹出逐条解决 UI。同步为可选功能，不登录不影响本地使用。

**技术栈：** Kotlin + Google Sign-In (Credential Manager) + Drive REST API v3 + Room Migration + Gson

---

## 文件结构总览

### 新增文件
| 文件 | 职责 |
|------|------|
| `data/local/entity/ConflictRecordEntity.kt` | 冲突记录 Room Entity |
| `data/local/dao/ConflictDao.kt` | 冲突记录 DAO |
| `data/remote/GoogleDriveClient.kt` | Drive REST API 封装（上传/下载/列出/删除） |
| `data/remote/SyncMetadata.kt | metadata.json 的 Gson 序列化模型 |
| `data/remote/SyncRepository.kt` | 同步数据仓库（协调 Drive 客户端 + 本地 DAO） |
| `data/remote/AuthManager.kt` | Google Sign-In + Token 管理 |
| `domain/model/ConflictRecord.kt` | 冲突记录领域模型 |
| `domain/service/SyncService.kt` | 同步引擎核心（上传/下载/冲突检测） |
| `ui/sync/SyncViewModel.kt` | 同步状态管理 + 冲突解决 |
| `ui/sync/SyncScreen.kt` | 冲突解决 UI 页面 |
| `ui/sync/SyncComponents.kt` | 同步状态组件（SnackBar、同步按钮） |
| `di/SyncModule.kt` | 同步相关 Hilt DI |

### 修改文件
| 文件 | 变更 |
|------|------|
| `app/build.gradle.kts` | 新增 Google Auth + Drive API 依赖 |
| `data/local/entity/BookEntity.kt` | 新增 syncVersion, lastSyncedAt, driveFileId 字段 |
| `data/local/entity/ReadingProgressEntity.kt` | 新增 syncVersion, lastSyncedAt 字段 |
| `data/local/entity/BookmarkEntity.kt` | 新增 syncVersion, lastSyncedAt 字段 |
| `data/local/entity/HighlightEntity.kt` | 新增 syncVersion, lastSyncedAt 字段 |
| `data/local/entity/NoteEntity.kt` | 新增 syncVersion, lastSyncedAt 字段 |
| `data/local/SimpleBookDatabase.kt` | version 2, 新增 ConflictRecordEntity, Migration |
| `data/repository/BookRepositoryImpl.kt` | toDomain/toEntity 映射新字段 |
| `data/repository/ReadingProgressRepository.kt` | 映射新字段 |
| `domain/model/Book.kt` | 新增 syncVersion, lastSyncedAt, driveFileId |
| `domain/model/ReadingProgress.kt` | 新增 syncVersion, lastSyncedAt |
| `domain/model/Bookmark.kt` | 新增 syncVersion, lastSyncedAt |
| `domain/model/Highlight.kt` | 新增 syncVersion, lastSyncedAt |
| `domain/model/Note.kt` | 新增 syncVersion, lastSyncedAt |
| `domain/service/ReadingService.kt` | 保存时自增 syncVersion |
| `ui/components/BookCard.kt` | 新增 percentage 参数，底部叠加进度条 |
| `ui/booklist/BookListViewModel.kt` | 加载每本书的阅读进度 |
| `ui/booklist/BookListScreen.kt` | 传递 percentage 给 BookCard + 同步按钮 |
| `ui/navigation/Screen.kt` | 新增 Sync 路由 |
| `ui/navigation/SimpleBookNavHost.kt` | 新增 SyncScreen 路由 |
| `di/DatabaseModule.kt` | 新增 ConflictDao provider |

---

## 任务 1：封面阅读进度显示（独立小功能）

**文件：**
- 修改：`ui/components/BookCard.kt`
- 修改：`ui/booklist/BookListViewModel.kt`
- 修改：`ui/booklist/BookListScreen.kt`

- [ ] **步骤 1：修改 BookCard 添加 percentage 参数和进度条**

在 `BookCard.kt` 中：

1. 为 `BookCard` 函数添加参数 `percentage: Double = 0.0`
2. 在封面 `Box` 内部（`AsyncImage` 或占位 `Text` 之后），添加进度叠加层：

```kotlin
// 在 Box { ... } 内部最后添加
if (percentage > 0.0) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(percentage.toFloat())
                    .background(Color.White)
            )
        }
        Text(
            text = "${(percentage * 100).toInt()}%",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 4.dp, bottom = 2.dp)
        )
    }
}
```

- [ ] **步骤 2：修改 BookListViewModel 加载阅读进度**

在 `BookListViewModel.kt` 中：

1. 注入 `ReadingService`
2. 添加 `bookProgress` StateFlow，类型为 `StateFlow<Map<Long, Double>>`（bookId → percentage）

```kotlin
@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookService: BookService,
    private val fileImportService: FileImportService,
    private val settingsDataStore: SettingsDataStore,
    private val readingService: ReadingService
) : ViewModel() {
    // ... 现有代码 ...

    private val _bookProgress = MutableStateFlow<Map<Long, Double>>(emptyMap())
    val bookProgress: StateFlow<Map<Long, Double>> = _bookProgress.asStateFlow()

    init {
        viewModelScope.launch {
            books.collect { bookList ->
                val progressMap = mutableMapOf<Long, Double>()
                for (book in bookList) {
                    val progress = readingService.loadProgress(book.id)
                    if (progress != null && progress.percentage > 0.0) {
                        progressMap[book.id] = progress.percentage
                    }
                }
                _bookProgress.value = progressMap
            }
        }
    }
}
```

- [ ] **步骤 3：修改 BookListScreen 传递 percentage**

在 `BookListScreen.kt` 中：

1. 收集 `bookProgress` state：`val progress by viewModel.bookProgress.collectAsState()`
2. 在渲染每个 `BookCard` 时传入 percentage：`percentage = progress[book.id] ?: 0.0`

- [ ] **步骤 4：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add -A
git commit -m "feat: display reading progress percentage on book covers"
```

---

## 任务 2：Gradle 依赖

**文件：**
- 修改：`app/build.gradle.kts`

- [ ] **步骤 1：添加 Google Auth + Drive API 依赖**

在 `dependencies` 块中添加：

```kotlin
// Google Drive Sync
implementation("com.google.android.gms:play-services-auth:21.3.0")
implementation("com.google.apis:google-api-services-drive:v3-rev20250511-2.0.0")
implementation("com.google.http-client:google-http-client-gson:1.46.3")
```

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL（可能需要下载新依赖）

- [ ] **步骤 3：Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add Google Auth and Drive API dependencies"
```

---

## 任务 3：Room 数据库迁移 — 实体扩展

**文件：**
- 修改：`data/local/entity/BookEntity.kt`
- 修改：`data/local/entity/ReadingProgressEntity.kt`
- 修改：`data/local/entity/BookmarkEntity.kt`
- 修改：`data/local/entity/HighlightEntity.kt`
- 修改：`data/local/entity/NoteEntity.kt`
- 修改：`data/local/SimpleBookDatabase.kt`

- [ ] **步骤 1：为 BookEntity 新增 3 个字段**

在 `BookEntity.kt` 中添加：

```kotlin
val syncVersion: Long = 1,
val lastSyncedAt: Long? = null,
val driveFileId: String? = null
```

- [ ] **步骤 2：为 ReadingProgressEntity 新增 2 个字段**

```kotlin
val syncVersion: Long = 1,
val lastSyncedAt: Long? = null
```

- [ ] **步骤 3：为 BookmarkEntity 新增 2 个字段**

```kotlin
val syncVersion: Long = 1,
val lastSyncedAt: Long? = null
```

- [ ] **步骤 4：为 HighlightEntity 新增 2 个字段**

```kotlin
val syncVersion: Long = 1,
val lastSyncedAt: Long? = null
```

- [ ] **步骤 5：为 NoteEntity 新增 2 个字段**

```kotlin
val syncVersion: Long = 1,
val lastSyncedAt: Long? = null
```

- [ ] **步骤 6：更新 SimpleBookDatabase — 版本 2 + Migration**

```kotlin
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
                db.execSQL("""
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
                """)
            }
        }
    }
}
```

- [ ] **步骤 7：更新 DatabaseModule 中的 provideDatabase 添加 migration**

```kotlin
@Provides
@Singleton
fun provideDatabase(
    @ApplicationContext context: Context
): SimpleBookDatabase =
    Room.databaseBuilder(
        context,
        SimpleBookDatabase::class.java,
        "simplebook.db"
    ).addMigrations(SimpleBookDatabase.MIGRATION_1_2).build()
```

同时在 `DatabaseModule` 中添加 `ConflictDao` provider。

- [ ] **步骤 8：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 9：Commit**

```bash
git add -A
git commit -m "feat: extend Room entities with sync fields and add migration v1→v2"
```

---

## 任务 4：领域模型更新 + Repository 映射

**文件：**
- 修改：`domain/model/Book.kt`
- 修改：`domain/model/ReadingProgress.kt`（如存在）
- 修改：`data/repository/BookRepositoryImpl.kt`
- 修改：`data/repository/ReadingProgressRepository.kt`
- 创建：`domain/model/ConflictRecord.kt`
- 创建：`data/local/entity/ConflictRecordEntity.kt`
- 创建：`data/local/dao/ConflictDao.kt`

- [ ] **步骤 1：更新 Book 领域模型**

在 `Book.kt` 中添加：

```kotlin
data class Book(
    val id: Long = 0,
    val title: String,
    val author: String = "",
    val filePath: String,
    val format: BookFormat,
    val coverPath: String? = null,
    val fileSize: Long = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long? = null,
    val syncVersion: Long = 1,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null
)
```

- [ ] **步骤 2：更新所有领域模型添加 syncVersion 和 lastSyncedAt**

对 ReadingProgress、Bookmark、Highlight、Note 领域模型添加相同两个字段（默认值 syncVersion=1, lastSyncedAt=null）。

- [ ] **步骤 3：更新 BookRepositoryImpl 的 toDomain/toEntity**

在映射函数中添加新字段。

- [ ] **步骤 4：更新 ReadingProgressRepository 的 toDomain/toEntity**

在映射函数中添加新字段。

- [ ] **步骤 5：创建 ConflictRecordEntity**

```kotlin
@Entity(
    tableName = "conflict_records",
    foreignKeys = [ForeignKey(
        entity = BookEntity::class,
        parentColumns = ["id"],
        childColumns = ["bookId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class ConflictRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val entityType: String, // "progress" / "bookmark" / "highlight" / "note"
    val entityId: Long,
    val localSyncVersion: Long,
    val remoteSyncVersion: Long,
    val localData: String,   // JSON
    val remoteData: String,  // JSON
    val createdAt: Long,
    val resolvedAt: Long? = null
)
```

- [ ] **步骤 6：创建 ConflictDao**

```kotlin
@Dao
interface ConflictDao {
    @Query("SELECT * FROM conflict_records WHERE resolvedAt IS NULL")
    fun getUnresolvedConflicts(): Flow<List<ConflictRecordEntity>>

    @Query("SELECT * FROM conflict_records WHERE resolvedAt IS NULL AND bookId = :bookId")
    suspend fun getUnresolvedForBook(bookId: Long): List<ConflictRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: ConflictRecordEntity)

    @Query("UPDATE conflict_records SET resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun markResolved(id: Long, resolvedAt: Long)

    @Query("DELETE FROM conflict_records WHERE bookId = :bookId AND resolvedAt IS NOT NULL")
    suspend fun deleteResolvedForBook(bookId: Long)
}
```

- [ ] **步骤 7：创建 ConflictRecord 领域模型**

```kotlin
data class ConflictRecord(
    val id: Long = 0,
    val bookId: Long,
    val entityType: String,
    val entityId: Long,
    val localSyncVersion: Long,
    val remoteSyncVersion: Long,
    val localData: String,
    val remoteData: String,
    val createdAt: Long,
    val resolvedAt: Long? = null
)
```

- [ ] **步骤 8：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 9：Commit**

```bash
git add -A
git commit -m "feat: update domain models with sync fields, add ConflictRecord entity and DAO"
```

---

## 任务 5：ReadingService 更新 — 保存时自增 syncVersion

**文件：**
- 修改：`domain/service/ReadingService.kt`

- [ ] **步骤 1：更新 saveProgress 自增 syncVersion**

```kotlin
suspend fun saveProgress(bookId: Long, chapterIndex: Int, charOffset: Long, percentage: Double) {
    val existing = readingProgressRepo.getProgress(bookId)
    val progress = if (existing != null) {
        existing.copy(
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            percentage = percentage,
            updatedAt = System.currentTimeMillis(),
            syncVersion = existing.syncVersion + 1
        )
    } else {
        ReadingProgress(
            bookId = bookId,
            chapterIndex = chapterIndex,
            charOffset = charOffset,
            percentage = percentage,
            updatedAt = System.currentTimeMillis()
        )
    }
    readingProgressRepo.saveProgress(progress)
    // Update lastReadAt on book
    bookRepo.getBookById(bookId)?.let { book ->
        bookRepo.updateBook(book.copy(
            lastReadAt = System.currentTimeMillis(),
            syncVersion = book.syncVersion + 1
        ))
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: auto-increment syncVersion on reading progress and book updates"
```

---

## 任务 6：AuthManager — Google Sign-In 认证

**文件：**
- 创建：`data/remote/AuthManager.kt`

- [ ] **步骤 1：创建 AuthManager**

```kotlin
package com.ebookreader.simplebook.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val signInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_APPFOLDER))
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    private val _signedInAccount = MutableStateFlow<GoogleSignInAccount?>(null)
    val signedInAccount: StateFlow<GoogleSignInAccount?> = _signedInAccount.asStateFlow()

    val isSignedIn: Boolean get() = _signedInAccount.value != null

    val signInIntent get() = signInClient.signInIntent

    init {
        _signedInAccount.value = GoogleSignIn.getLastSignedInAccount(context)
    }

    suspend fun handleSignInResult(data: android.content.Intent) {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        val account = task.getResult(ApiException::class.java)
        _signedInAccount.value = account
    }

    fun signOut() {
        signInClient.signOut()
        _signedInAccount.value = null
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: add AuthManager for Google Sign-In with Drive scope"
```

---

## 任务 7：SyncMetadata — 序列化模型

**文件：**
- 创建：`data/remote/SyncMetadata.kt`

- [ ] **步骤 1：创建 SyncMetadata 数据类**

```kotlin
package com.ebookreader.simplebook.data.remote

import com.google.gson.annotations.SerializedName

data class SyncManifest(
    val books: Map<String, String> = emptyMap() // bookId → driveFolderId
)

data class BookMetadata(
    val bookId: Long,
    val title: String,
    val author: String,
    val format: String,
    val fileSize: Long,
    val coverPath: String? = null,
    val syncVersion: Long = 1,
    val updatedAt: Long,
    val progress: ProgressMetadata? = null,
    val bookmarks: List<BookmarkMetadata> = emptyList(),
    val highlights: List<HighlightMetadata> = emptyList(),
    val notes: List<NoteMetadata> = emptyList()
)

data class ProgressMetadata(
    val chapterIndex: Int,
    val charOffset: Long,
    val percentage: Double,
    val syncVersion: Long,
    val updatedAt: Long
)

data class BookmarkMetadata(
    val chapterIndex: Int,
    val charOffset: Long,
    val name: String,
    val syncVersion: Long,
    val createdAt: Long
)

data class HighlightMetadata(
    val chapterIndex: Int,
    val startOffset: Long,
    val endOffset: Long,
    val color: Int,
    val note: String?,
    val syncVersion: Long,
    val createdAt: Long
)

data class NoteMetadata(
    val highlightId: Long?,
    val chapterIndex: Int,
    val charOffset: Long,
    val content: String,
    val syncVersion: Long,
    val createdAt: Long
)
```

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: add SyncMetadata serialization models for Drive sync"
```

---

## 任务 8：GoogleDriveClient — Drive REST API 封装

**文件：**
- 创建：`data/remote/GoogleDriveClient.kt`

- [ ] **步骤 1：创建 GoogleDriveClient**

```kotlin
package com.ebookreader.simplebook.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager
) {
    private val drive: Drive?
        get() = authManager.signedInAccount.value?.let { account ->
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_APPFOLDER)
            )
            credential.selectedAccount = account.account
            Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("SimpleBook").build()
        }

    suspend fun uploadFile(
        folderId: String,
        fileName: String,
        content: ByteArray,
        mimeType: String = "application/octet-stream"
    ): String? {
        val drive = drive ?: return null
        val existing = findFileInFolder(folderId, fileName)
        return if (existing != null) {
            drive.files().update(existing, com.google.api.services.drive.model.File().apply {
                name = fileName
            }, ByteArrayContent(mimeType, content)).execute().id
        } else {
            drive.files().create(
                com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf(folderId)
                },
                ByteArrayContent(mimeType, content)
            ).setFields("id").execute().id
        }
    }

    suspend fun uploadBookFile(
        folderId: String,
        fileName: String,
        localFile: File,
        mimeType: String
    ): String? {
        val drive = drive ?: return null
        val existing = findFileInFolder(folderId, fileName)
        return if (existing != null) {
            drive.files().update(existing, com.google.api.services.drive.model.File(), FileContent(mimeType, localFile))
                .execute().id
        } else {
            drive.files().create(
                com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf(folderId)
                },
                FileContent(mimeType, localFile)
            ).setFields("id").execute().id
        }
    }

    suspend fun downloadFile(fileId: String): ByteArray? {
        val drive = drive ?: return null
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        return out.toByteArray()
    }

    suspend fun downloadFileTo(fileId: String, targetFile: File) {
        val drive = drive ?: return
        targetFile.outputStream().use { out ->
            drive.files().get(fileId).executeMediaAndDownloadTo(out)
        }
    }

    suspend fun createFolder(name: String, parentId: String): String? {
        val drive = drive ?: return null
        val existing = findFileInFolder(parentId, name)
        if (existing != null) return existing
        return drive.files().create(
            com.google.api.services.drive.model.File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }
        ).setFields("id").execute().id
    }

    suspend fun getAppFolderId(): String? {
        val drive = drive ?: return null
        return drive.files().get("appfolder").setFields("id").execute().id
    }

    suspend fun findFileInFolder(folderId: String, fileName: String): String? {
        val drive = drive ?: return null
        val query = "'$folderId' in parents and name='$fileName' and trashed=false"
        val result: FileList = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id)")
            .execute()
        return result.files.firstOrNull()?.id
    }

    suspend fun listFilesInFolder(folderId: String): List<Pair<String, String>> {
        val drive = drive ?: return emptyList()
        val query = "'$folderId' in parents and trashed=false"
        val result: FileList = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id, name)")
            .execute()
        return result.files.map { it.name to it.id }
    }

    suspend fun deleteFile(fileId: String) {
        val drive = drive ?: return
        drive.files().delete(fileId).execute()
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: add GoogleDriveClient wrapping Drive REST API for app folder operations"
```

---

## 任务 9：SyncService — 同步引擎核心

**文件：**
- 创建：`domain/service/SyncService.kt`

- [ ] **步骤 1：创建 SyncService**

核心同步逻辑：上传变更、拉取远端、检测冲突。

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.local.dao.ConflictDao
import com.ebookreader.simplebook.data.local.entity.ConflictRecordEntity
import com.ebookreader.simplebook.data.remote.BookMetadata
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.data.remote.SyncManifest
import com.ebookreader.simplebook.data.remote.SyncMetadata
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.Book
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncService @Inject constructor(
    private val driveClient: GoogleDriveClient,
    private val authManager: AuthManager,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val conflictDao: ConflictDao,
    private val gson: Gson
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: Flow<SyncStatus> = _syncStatus.asStateFlow()

    private val _conflictCount = MutableStateFlow(0)
    val conflictCount: Flow<Int> = _conflictCount.asStateFlow()

    sealed class SyncStatus {
        data object Idle : SyncStatus()
        data object Syncing : SyncStatus()
        data class Error(val message: String) : SyncStatus()
        data object Success : SyncStatus()
    }

    suspend fun syncAll() {
        if (!authManager.isSignedIn) return
        _syncStatus.value = SyncStatus.Syncing
        try {
            pullFromRemote()
            pushToRemote()
            _syncStatus.value = SyncStatus.Success
            updateConflictCount()
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
        }
    }

    private suspend fun pushToRemote() {
        val appFolderId = driveClient.getAppFolderId() ?: return
        val manifest = loadManifest(appFolderId)
        val books = bookRepository.getAllBooksNow() // 需要添加非 Flow 版本

        for (book in books) {
            if (book.lastSyncedAt == null || book.syncVersion > 1) {
                uploadBook(appFolderId, manifest, book)
            }
        }
    }

    private suspend fun uploadBook(
        appFolderId: String,
        manifest: SyncManifest,
        book: Book
    ) {
        val folderName = "book_${book.id}"
        val folderId = driveClient.createFolder(folderName, appFolderId) ?: return

        // Upload book file (only if not yet uploaded)
        val localFile = File(book.filePath)
        if (localFile.exists()) {
            val mimeType = if (book.format.name == "EPUB") "application/epub+zip" else "text/plain"
            driveClient.uploadBookFile(folderId, localFile.name, localFile, mimeType)
        }

        // Build metadata
        val metadata = buildMetadata(book)

        // Upload metadata
        val json = gson.toJson(metadata)
        driveClient.uploadFile(folderId, "metadata.json", json.toByteArray(), "application/json")

        // Update book sync status
        bookRepository.updateBook(book.copy(
            driveFileId = folderId,
            lastSyncedAt = System.currentTimeMillis()
        ))
    }

    private suspend fun pullFromRemote() {
        val appFolderId = driveClient.getAppFolderId() ?: return
        val folders = driveClient.listFilesInFolder(appFolderId)

        for ((folderName, folderId) in folders) {
            if (!folderName.startsWith("book_")) continue
            val metadataJson = driveClient.findFileInFolder(folderId, "metadata.json")?.let {
                driveClient.downloadFile(it)
            } ?: continue

            val metadata = gson.fromJson(String(metadataJson), BookMetadata::class.java)
            val localBook = bookRepository.getBookByDriveFileId(folderId)

            if (localBook == null) {
                // New book from remote - download and import
                downloadBookFromRemote(folderId, metadata)
            } else {
                // Merge/detect conflicts
                mergeBook(localBook, metadata, folderId)
            }
        }
    }

    private suspend fun mergeBook(
        localBook: Book,
        remote: BookMetadata,
        folderId: String
    ) {
        // Compare progress
        val localProgress = readingProgressRepository.getProgress(localBook.id)
        if (localProgress != null && remote.progress != null) {
            if (remote.progress.syncVersion > localProgress.syncVersion
                && localProgress.lastSyncedAt != null
                && localProgress.updatedAt > (localProgress.lastSyncedAt ?: 0)
            ) {
                // Conflict!
                conflictDao.insert(ConflictRecordEntity(
                    bookId = localBook.id,
                    entityType = "progress",
                    entityId = localProgress.id,
                    localSyncVersion = localProgress.syncVersion,
                    remoteSyncVersion = remote.progress.syncVersion,
                    localData = gson.toJson(localProgress),
                    remoteData = gson.toJson(remote.progress),
                    createdAt = System.currentTimeMillis()
                ))
            } else if (remote.progress.syncVersion > localProgress.syncVersion) {
                // Remote is newer, just apply
                applyRemoteProgress(localBook.id, remote.progress)
            }
        } else if (remote.progress != null && localProgress == null) {
            applyRemoteProgress(localBook.id, remote.progress)
        }
        // Similar merge logic for bookmarks, highlights, notes...
    }

    private suspend fun applyRemoteProgress(bookId: Long, remote: ProgressMetadata) {
        val existing = readingProgressRepository.getProgress(bookId)
        val progress = if (existing != null) {
            existing.copy(
                chapterIndex = remote.chapterIndex,
                charOffset = remote.charOffset,
                percentage = remote.percentage,
                updatedAt = remote.updatedAt,
                syncVersion = remote.syncVersion,
                lastSyncedAt = System.currentTimeMillis()
            )
        } else {
            com.ebookreader.simplebook.domain.model.ReadingProgress(
                bookId = bookId,
                chapterIndex = remote.chapterIndex,
                charOffset = remote.charOffset,
                percentage = remote.percentage,
                updatedAt = remote.updatedAt,
                syncVersion = remote.syncVersion,
                lastSyncedAt = System.currentTimeMillis()
            )
        }
        readingProgressRepository.saveProgress(progress)
    }

    private suspend fun downloadBookFromRemote(folderId: String, metadata: BookMetadata) {
        // Download book file from Drive, import to local storage
        // Implementation depends on FileImportService integration
    }

    private suspend fun buildMetadata(book: Book): BookMetadata {
        val progress = readingProgressRepository.getProgress(book.id)
        // Build full BookMetadata from local data
        return BookMetadata(
            bookId = book.id,
            title = book.title,
            author = book.author,
            format = book.format.name,
            fileSize = book.fileSize,
            syncVersion = book.syncVersion,
            updatedAt = book.lastReadAt ?: book.addedAt,
            progress = progress?.let {
                ProgressMetadata(
                    chapterIndex = it.chapterIndex,
                    charOffset = it.charOffset,
                    percentage = it.percentage,
                    syncVersion = it.syncVersion,
                    updatedAt = it.updatedAt
                )
            }
        )
    }

    private suspend fun loadManifest(appFolderId: String): SyncManifest {
        val data = driveClient.findFileInFolder(appFolderId, "sync-manifest.json")?.let {
            driveClient.downloadFile(it)
        } ?: return SyncManifest()
        return gson.fromJson(String(data), SyncManifest::class.java)
    }

    private suspend fun updateConflictCount() {
        _conflictCount.value = conflictDao.getUnresolvedConflictsNow().size
    }

    suspend fun resolveConflict(conflictId: Long, useRemote: Boolean) {
        val conflicts = conflictDao.getUnresolvedForBookNow(-1) // 获取所有
        val conflict = conflicts.firstOrNull { it.id == conflictId } ?: return

        if (useRemote) {
            applyRemoteData(conflict)
        }
        // 如果保留本地，不做任何操作，只标记已解决

        conflictDao.markResolved(conflictId, System.currentTimeMillis())
        updateConflictCount()
    }

    suspend fun resolveAllConflicts(useRemote: Boolean) {
        val conflicts = conflictDao.getUnresolvedForBookNow(-1)
        for (conflict in conflicts) {
            if (useRemote) {
                applyRemoteData(conflict)
            }
            conflictDao.markResolved(conflict.id, System.currentTimeMillis())
        }
        updateConflictCount()
    }

    private suspend fun applyRemoteData(conflict: ConflictRecordEntity) {
        when (conflict.entityType) {
            "progress" -> {
                val remote = gson.fromJson(conflict.remoteData, ProgressMetadata::class.java)
                applyRemoteProgress(conflict.bookId, remote)
            }
            // bookmark/highlight/note 类似处理
        }
    }
}
```

注意：此步骤创建的 SyncService 是骨架，需要在实际实现中补充书签/高亮/笔记的同步逻辑。DAO 需要补充非 Flow 的查询方法（如 `getUnresolvedConflictsNow()`）。

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: add SyncService with upload, download, and conflict detection logic"
```

---

## 任务 10：Hilt DI 装配

**文件：**
- 创建：`di/SyncModule.kt`
- 修改：`di/DatabaseModule.kt`

- [ ] **步骤 1：创建 SyncModule**

```kotlin
package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.dao.ConflictDao
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.data.remote.SyncRepository
import com.ebookreader.simplebook.domain.service.SyncService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideSyncService(
        driveClient: GoogleDriveClient,
        authManager: AuthManager,
        syncRepository: SyncRepository,
        conflictDao: ConflictDao,
        gson: Gson
    ): SyncService = SyncService(driveClient, authManager, syncRepository, conflictDao, gson)
}
```

- [ ] **步骤 2：在 DatabaseModule 添加 ConflictDao provider**

```kotlin
@Provides
fun provideConflictDao(db: SimpleBookDatabase): ConflictDao = db.conflictDao()
```

- [ ] **步骤 3：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add -A
git commit -m "feat: add Hilt DI module for sync dependencies"
```

---

## 任务 11：SyncViewModel — 同步状态管理

**文件：**
- 创建：`ui/sync/SyncViewModel.kt`

- [ ] **步骤 1：创建 SyncViewModel**

```kotlin
package com.ebookreader.simplebook.ui.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.domain.model.ConflictRecord
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.SyncService.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncService: SyncService,
    private val authManager: AuthManager
) : ViewModel() {

    val syncStatus: StateFlow<SyncStatus> = syncService.syncStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncStatus.Idle)

    val conflictCount: StateFlow<Int> = syncService.conflictCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isSignedIn: Boolean get() = authManager.isSignedIn

    fun syncNow() {
        viewModelScope.launch { syncService.syncAll() }
    }

    fun resolveConflict(conflictId: Long, useRemote: Boolean) {
        viewModelScope.launch { syncService.resolveConflict(conflictId, useRemote) }
    }

    fun resolveAllConflicts(useRemote: Boolean) {
        viewModelScope.launch { syncService.resolveAllConflicts(useRemote) }
    }

    fun signOut() {
        authManager.signOut()
    }
}
```

- [ ] **步骤 2：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 3：Commit**

```bash
git add -A
git commit -m "feat: add SyncViewModel for sync state management"
```

---

## 任务 12：同步 UI — 设置页同步区域 + 书架同步按钮

**文件：**
- 创建：`ui/sync/SyncComponents.kt`
- 修改：`ui/settings/SettingsScreen.kt`（添加同步区域）
- 修改：`ui/booklist/BookListScreen.kt`（添加同步图标）

- [ ] **步骤 1：创建 SyncComponents**

可复用的同步状态组件：同步按钮、SnackBar 提示。

```kotlin
package com.ebookreader.simplebook.ui.sync

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SyncStatusIcon(
    isSyncing: Boolean,
    isSignedIn: Boolean,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = when {
                isSyncing -> Icons.Default.CloudSync
                isSignedIn -> Icons.Default.CloudDone
                else -> Icons.Default.CloudOff
            },
            contentDescription = "Sync"
        )
    }
}

@Composable
fun ConflictSnackBar(
    conflictCount: Int,
    onClick: () -> Unit
) {
    if (conflictCount > 0) {
        Snackbar(
            action = { Text("查看") },
            onAction = onClick
        ) {
            Text("有 $conflictCount 条数据冲突需要解决")
        }
    }
}
```

- [ ] **步骤 2：在 SettingsScreen 添加同步管理区域**

在 Settings 页面添加「Google Drive 同步」区域：
- 未登录：显示「登录 Google Drive」按钮
- 已登录：显示账户信息 + 「立即同步」按钮 + 「退出登录」按钮

- [ ] **步骤 3：在 BookListScreen 顶部栏添加同步状态图标**

- [ ] **步骤 4：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add -A
git commit -m "feat: add sync UI components, sync section in settings, sync icon in bookshelf"
```

---

## 任务 13：冲突解决页面

**文件：**
- 创建：`ui/sync/SyncScreen.kt`
- 修改：`ui/navigation/Screen.kt`
- 修改：`ui/navigation/SimpleBookNavHost.kt`

- [ ] **步骤 1：在 Screen.kt 添加 Sync 路由**

```kotlin
data object Sync : Screen("sync")
```

- [ ] **步骤 2：在 SimpleBookNavHost.kt 添加 SyncScreen 路由**

```kotlin
composable(Screen.Sync.route) {
    SyncScreen(onNavigateBack = { navController.popBackStack() })
}
```

- [ ] **步骤 3：创建 SyncScreen**

全屏冲突解决页面，逐条展示冲突：
- 顶部：标题 + 未解决数量
- 中间：LazyColumn 逐条显示冲突项
- 每项：类型标签 + 本地预览 vs 远端预览 + 「保留本地」/「使用远端」按钮
- 底部：「全部保留本地」/「全部使用远端」快捷按钮

- [ ] **步骤 4：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add -A
git commit -m "feat: add conflict resolution SyncScreen with per-item resolution UI"
```

---

## 任务 14：自动同步触发

**文件：**
- 修改：`SimpleBookApp.kt`（或 `MainActivity.kt`）

- [ ] **步骤 1：在 MainActivity 添加 lifecycle 同步触发**

使用 `lifecycleScope` + `LifecycleEventObserver`：
- `ON_START`：触发 pullFromRemote
- `ON_STOP`：触发 pushToRemote（上传当前进度）

在 `MainActivity.kt` 注入 `SyncService`，在 lifecycle 事件中调用 `syncService.syncAll()`。

- [ ] **步骤 2：添加登录 Intent 处理**

在 `MainActivity` 的 `onActivityResult` 中处理 Google Sign-In 的返回结果，传递给 `AuthManager.handleSignInResult()`。

- [ ] **步骤 3：构建验证**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add -A
git commit -m "feat: add auto-sync on app lifecycle events and Google Sign-In result handling"
```

---

## 任务 15：端到端构建验证 + 清理

**文件：**
- 所有新增/修改文件

- [ ] **步骤 1：完整构建**

运行：`JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home" ./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：代码清理**

检查所有新增文件：
- 移除未使用的 import
- 确认所有新增类有 Hilt 注解
- 确认非登录状态下所有本地功能正常（无编译期依赖 Google 登录）

- [ ] **步骤 3：最终 Commit**

```bash
git add -A
git commit -m "feat: v1.0 complete — Google Drive sync + cover progress display"
```

---

## 实现顺序总结

1. **任务 1**：封面进度显示（独立，最先完成，可立即验证）
2. **任务 2**：Gradle 依赖
3. **任务 3**：Room 迁移（实体扩展）
4. **任务 4**：领域模型 + Repository 映射
5. **任务 5**：ReadingService 更新
6. **任务 6**：AuthManager
7. **任务 7**：SyncMetadata 模型
8. **任务 8**：GoogleDriveClient
9. **任务 9**：SyncService 核心
10. **任务 10**：Hilt DI 装配
11. **任务 11**：SyncViewModel
12. **任务 12**：同步 UI 组件
13. **任务 13**：冲突解决页面
14. **任务 14**：自动同步触发
15. **任务 15**：端到端验证

**依赖关系：** 任务 1 完全独立。任务 2-5 为数据层基础。任务 6-8 为 Drive 层（依赖 2-5）。任务 9 依赖 6-8。任务 10-14 依赖 9。任务 15 为最终验证。
