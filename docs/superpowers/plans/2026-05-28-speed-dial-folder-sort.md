# Speed Dial FAB + 文件夹分类 + 排序功能 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 为书架页面实现 Speed Dial FAB 交互、文件夹分类（排他归属 + Drive 同步）、用户可选排序。

**架构：** Book 表加 `folderId` nullable 字段 + 新增 Folder 实体，统一通过 LWW 同步。UI 层引入 `ShelfItem` 密封类统一渲染书籍和文件夹卡片。文件夹二级视图通过 ViewModel 状态切换，不新增路由。

**技术栈：** Kotlin, Jetpack Compose (M3), Room, Hilt, DataStore Preferences

---

## 文件结构

### 新增文件
| 文件 | 职责 |
|------|------|
| `domain/model/SortOrder.kt` | 排序枚举 |
| `domain/model/Folder.kt` | 文件夹领域模型 |
| `domain/model/ShelfItem.kt` | 书架统一渲染模型 |
| `data/local/entity/FolderEntity.kt` | Room 实体 |
| `data/local/dao/FolderDao.kt` | 文件夹 DAO |
| `data/repository/FolderRepository.kt` | 文件夹仓库接口 |
| `data/repository/FolderRepositoryImpl.kt` | 文件夹仓库实现 |
| `domain/service/FolderService.kt` | 文件夹业务逻辑 |
| `ui/components/SpeedDialFAB.kt` | Speed Dial FAB 组件 |
| `ui/components/FolderCard.kt` | 文件夹卡片（网格 + 列表） |

### 修改文件
| 文件 | 变更 |
|------|------|
| `data/local/entity/BookEntity.kt` | 新增 `folderId` 字段 |
| `domain/model/Book.kt` | 新增 `folderId` 字段 |
| `data/local/dao/BookDao.kt` | 新增 `getShelfBooks()`、`getBooksInFolder()` 查询 |
| `data/local/SimpleBookDatabase.kt` | 新增 FolderEntity、MIGRATION_3_4 |
| `data/repository/BookRepository.kt` | 新增文件夹相关方法 |
| `data/repository/BookRepositoryImpl.kt` | 新增文件夹相关方法实现 |
| `di/DatabaseModule.kt` | 提供 FolderDao |
| `data/local/SettingsDataStore.kt` | 新增 SortOrder 持久化 |
| `ui/booklist/BookListViewModel.kt` | 重构为 ShelfItem + 文件夹/排序状态 |
| `ui/booklist/BookListScreen.kt` | SpeedDialFAB + 文件夹二级视图 + 对话框 |
| `ui/components/AdaptiveBookGrid.kt` | 支持 ShelfItem 渲染 |
| `domain/model/AppStrings.kt` | 新增文件夹/排序相关字符串 |

---

## 任务 1：SortOrder 枚举 + SettingsDataStore 扩展

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/domain/model/SortOrder.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/local/SettingsDataStore.kt`

- [ ] **步骤 1：创建 SortOrder 枚举**

创建 `domain/model/SortOrder.kt`：

```kotlin
package com.ebookreader.simplebook.domain.model

enum class SortOrder(val key: String) {
    LAST_READ("last_read"),
    NAME("name");

    companion object {
        fun fromKey(key: String?): SortOrder =
            entries.find { it.key == key } ?: LAST_READ
    }
}
```

- [ ] **步骤 2：扩展 SettingsDataStore**

在 `SettingsDataStore.kt` 中：

a) 在 companion object 中添加：
```kotlin
val SORT_ORDER = stringPreferencesKey("sort_order")
```

b) 修改 `settings` Flow，将 `sortOrder` 加入 `ReaderSettings`（见下一步）。

c) 添加方法：
```kotlin
suspend fun updateSortOrder(sortOrder: SortOrder) {
    context.dataStore.edit { it[SORT_ORDER] = sortOrder.key }
}
```

- [ ] **步骤 3：扩展 ReaderSettings 模型**

在 `domain/model/ReaderSettings.kt` 中添加 `sortOrder` 字段：

```kotlin
data class ReaderSettings(
    val fontSize: Float = 16f,
    val lineHeight: Float = 1.5f,
    val theme: ReaderTheme = ReaderTheme.DEFAULT_WHITE,
    val language: String = "zh",
    val layoutMode: LayoutMode = LayoutMode.LARGE_GRID,
    val sortOrder: SortOrder = SortOrder.LAST_READ
)
```

在 `SettingsDataStore.settings` Flow 中映射：
```kotlin
sortOrder = SortOrder.fromKey(prefs[SORT_ORDER])
```

- [ ] **步骤 4：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 5：Commit**

```bash
git add domain/model/SortOrder.kt domain/model/ReaderSettings.kt data/local/SettingsDataStore.kt
git commit -m "feat: add SortOrder enum and persist sort preference in SettingsDataStore"
```

---

## 任务 2：Folder 实体 + Room 数据库迁移

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/data/local/entity/FolderEntity.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/local/entity/BookEntity.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/local/SimpleBookDatabase.kt`

- [ ] **步骤 1：创建 FolderEntity**

创建 `data/local/entity/FolderEntity.kt`：

```kotlin
package com.ebookreader.simplebook.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val uuid: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null
)
```

- [ ] **步骤 2：BookEntity 新增 folderId 字段**

在 `BookEntity` 中添加：
```kotlin
val folderId: String? = null
```

- [ ] **步骤 3：编写 MIGRATION_3_4**

在 `SimpleBookDatabase.kt` 中：

a) 将 `version = 3` 改为 `version = 4`

b) 在 `entities` 列表中添加 `FolderEntity::class`

c) 添加 `abstract fun folderDao(): FolderDao`

d) 在 companion object 中添加：
```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 创建 folders 表
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

        // books 表新增 folderId 列
        db.execSQL("ALTER TABLE books ADD COLUMN folderId TEXT")
    }
}
```

e) 在 `DatabaseModule.provideDatabase` 中添加 `MIGRATION_3_4`

- [ ] **步骤 4：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 5：Commit**

```bash
git add data/local/entity/FolderEntity.kt data/local/entity/BookEntity.kt data/local/SimpleBookDatabase.kt di/DatabaseModule.kt
git commit -m "feat: add FolderEntity, BookEntity.folderId, and Room migration 3→4"
```

---

## 任务 3：FolderDao + FolderRepository + FolderService

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/data/local/dao/FolderDao.kt`
- 创建：`app/src/main/java/com/ebookreader/simplebook/domain/model/Folder.kt`
- 创建：`app/src/main/java/com/ebookreader/simplebook/data/repository/FolderRepository.kt`
- 创建：`app/src/main/java/com/ebookreader/simplebook/data/repository/FolderRepositoryImpl.kt`
- 创建：`app/src/main/java/com/ebookreader/simplebook/domain/service/FolderService.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/di/DatabaseModule.kt`

- [ ] **步骤 1：创建 Folder 领域模型**

创建 `domain/model/Folder.kt`：

```kotlin
package com.ebookreader.simplebook.domain.model

data class Folder(
    val uuid: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long? = null,
    val driveFileId: String? = null
)
```

- [ ] **步骤 2：创建 FolderDao**

创建 `data/local/dao/FolderDao.kt`：

```kotlin
package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebookreader.simplebook.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE uuid = :uuid")
    suspend fun getFolderByUuid(uuid: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM books WHERE folderId = :folderId AND isDeleted = 0")
    suspend fun getBookCountInFolder(folderId: String): Int

    @Query("SELECT * FROM folders WHERE isDeleted = 1")
    suspend fun getDeletedFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersIncludingDeleted(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE driveFileId = :driveFileId")
    suspend fun getFolderByDriveFileId(driveFileId: String): FolderEntity?
}
```

- [ ] **步骤 3：创建 FolderRepository 接口**

创建 `data/repository/FolderRepository.kt`：

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAllFolders(): Flow<List<Folder>>
    suspend fun getFolderByUuid(uuid: String): Folder?
    suspend fun getFolderByDriveFileId(driveFileId: String): Folder?
    suspend fun addFolder(folder: Folder): String
    suspend fun updateFolder(folder: Folder)
    suspend fun softDeleteFolder(uuid: String)
    suspend fun getBookCountInFolder(folderId: String): Int
    suspend fun getAllFoldersIncludingDeleted(): List<Folder>
}
```

- [ ] **步骤 4：创建 FolderRepositoryImpl**

创建 `data/repository/FolderRepositoryImpl.kt`：

```kotlin
package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.FolderDao
import com.ebookreader.simplebook.data.local.entity.FolderEntity
import com.ebookreader.simplebook.domain.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> =
        folderDao.getAllFolders().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getFolderByUuid(uuid: String): Folder? =
        folderDao.getFolderByUuid(uuid)?.toDomain()

    override suspend fun getFolderByDriveFileId(driveFileId: String): Folder? =
        folderDao.getFolderByDriveFileId(driveFileId)?.toDomain()

    override suspend fun addFolder(folder: Folder): String {
        folderDao.insert(folder.toEntity())
        return folder.uuid
    }

    override suspend fun updateFolder(folder: Folder) =
        folderDao.update(folder.toEntity())

    override suspend fun softDeleteFolder(uuid: String) =
        folderDao.softDelete(uuid)

    override suspend fun getBookCountInFolder(folderId: String): Int =
        folderDao.getBookCountInFolder(folderId)

    override suspend fun getAllFoldersIncludingDeleted(): List<Folder> =
        folderDao.getAllFoldersIncludingDeleted().map { it.toDomain() }

    private fun FolderEntity.toDomain() = Folder(
        uuid = uuid, name = name, createdAt = createdAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, driveFileId = driveFileId
    )

    private fun Folder.toEntity() = FolderEntity(
        uuid = uuid, name = name, createdAt = createdAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, driveFileId = driveFileId
    )
}
```

- [ ] **步骤 5：创建 FolderService**

创建 `domain/service/FolderService.kt`：

```kotlin
package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.FolderRepository
import com.ebookreader.simplebook.domain.model.Folder
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderService @Inject constructor(
    private val folderRepository: FolderRepository
) {
    fun getAllFolders(): Flow<List<Folder>> = folderRepository.getAllFolders()

    suspend fun createFolder(name: String): Folder {
        val folder = Folder(name = name)
        folderRepository.addFolder(folder)
        return folder
    }

    suspend fun softDeleteFolder(uuid: String) = folderRepository.softDeleteFolder(uuid)

    suspend fun getBookCountInFolder(folderId: String): Int =
        folderRepository.getBookCountInFolder(folderId)

    suspend fun getFolderByUuid(uuid: String): Folder? =
        folderRepository.getFolderByUuid(uuid)
}
```

- [ ] **步骤 6：注册 FolderDao 到 DI**

在 `DatabaseModule.kt` 中添加：
```kotlin
@Provides
fun provideFolderDao(db: SimpleBookDatabase): FolderDao = db.folderDao()
```

- [ ] **步骤 7：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 8：Commit**

```bash
git add domain/model/Folder.kt data/local/dao/FolderDao.kt data/repository/FolderRepository.kt data/repository/FolderRepositoryImpl.kt domain/service/FolderService.kt di/DatabaseModule.kt
git commit -m "feat: add FolderDao, FolderRepository, FolderService for folder data layer"
```

---

## 任务 4：BookDao + BookRepository + BookService 扩展

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/local/dao/BookDao.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/repository/BookRepository.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/repository/BookRepositoryImpl.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/model/Book.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/service/BookService.kt`

- [ ] **步骤 1：Book 领域模型添加 folderId**

在 `Book.kt` 的 `driveFileId` 字段之前添加：
```kotlin
val folderId: String? = null,
```

- [ ] **步骤 2：BookDao 新增查询方法**

在 `BookDao.kt` 中添加：
```kotlin
@Query("SELECT * FROM books WHERE folderId IS NULL AND isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
fun getShelfBooks(): Flow<List<BookEntity>>

@Query("SELECT * FROM books WHERE folderId = :folderId AND isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
fun getBooksInFolder(folderId: String): Flow<List<BookEntity>>
```

- [ ] **步骤 3：BookRepository 接口新增方法**

在 `BookRepository.kt` 中添加：
```kotlin
fun getShelfBooks(): Flow<List<Book>>
fun getBooksInFolder(folderId: String): Flow<List<Book>>
suspend fun moveBookToFolder(bookUuid: String, folderId: String?)
```

- [ ] **步骤 4：BookRepositoryImpl 新增方法实现**

a) 添加 `toDomain()` 中的 `folderId` 映射：
```kotlin
private fun BookEntity.toDomain() = Book(
    uuid = uuid, title = title, author = author, filePath = filePath,
    format = BookFormat.valueOf(format), coverPath = coverPath, fileSize = fileSize,
    addedAt = addedAt, lastReadAt = lastReadAt, updatedAt = updatedAt,
    isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, folderId = folderId,
    driveFileId = driveFileId
)
```

b) 添加 `toEntity()` 中的 `folderId` 映射：
```kotlin
private fun Book.toEntity() = BookEntity(
    uuid = uuid, title = title, author = author, filePath = filePath,
    format = format.name, coverPath = coverPath, fileSize = fileSize,
    addedAt = addedAt, lastReadAt = lastReadAt, updatedAt = updatedAt,
    isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, folderId = folderId,
    driveFileId = driveFileId
)
```

c) 添加新方法：
```kotlin
override fun getShelfBooks(): Flow<List<Book>> =
    bookDao.getShelfBooks().map { entities -> entities.map { it.toDomain() } }

override fun getBooksInFolder(folderId: String): Flow<List<Book>> =
    bookDao.getBooksInFolder(folderId).map { entities -> entities.map { it.toDomain() } }

override suspend fun moveBookToFolder(bookUuid: String, folderId: String?) {
    val book = bookDao.getBookByUuid(bookUuid) ?: return
    bookDao.update(book.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
}
```

- [ ] **步骤 5：BookService 新增方法**

在 `BookService.kt` 中添加：
```kotlin
fun getShelfBooks(): Flow<List<Book>> = bookRepository.getShelfBooks()

fun getBooksInFolder(folderId: String): Flow<List<Book>> = bookRepository.getBooksInFolder(folderId)

suspend fun moveBookToFolder(bookUuid: String, folderId: String?) =
    bookRepository.moveBookToFolder(bookUuid, folderId)
```

- [ ] **步骤 6：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 7：Commit**

```bash
git add domain/model/Book.kt data/local/dao/BookDao.kt data/repository/BookRepository.kt data/repository/BookRepositoryImpl.kt domain/service/BookService.kt
git commit -m "feat: extend Book with folderId, add shelf/folder queries and moveBookToFolder"
```

---

## 任务 5：ShelfItem 密封类

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/domain/model/ShelfItem.kt`

- [ ] **步骤 1：创建 ShelfItem**

创建 `domain/model/ShelfItem.kt`：

```kotlin
package com.ebookreader.simplebook.domain.model

sealed class ShelfItem {
    data class BookItem(val book: Book, val progress: Double) : ShelfItem()
    data class FolderItem(val folder: Folder, val bookCount: Int) : ShelfItem()
}
```

- [ ] **步骤 2：Commit**

```bash
git add domain/model/ShelfItem.kt
git commit -m "feat: add ShelfItem sealed class for unified shelf rendering"
```

---

## 任务 6：SpeedDialFAB 组件

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/ui/components/SpeedDialFAB.kt`

- [ ] **步骤 1：创建 SpeedDialFAB**

创建 `ui/components/SpeedDialFAB.kt`：

```kotlin
package com.ebookreader.simplebook.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

data class SpeedDialItem(
    val icon: @Composable () -> Unit,
    val label: String,
    val onClick: () -> Unit
)

@Composable
fun SpeedDialFAB(
    items: List<SpeedDialItem>,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        // 展开时的半透明遮罩
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // 透明可点击层，点击关闭
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickableNoRipple { onDismiss() }
            )
        }

        Column(
            horizontalAlignment = Alignment.End
        ) {
            // Mini FABs（从下到上显示，所以 items 倒序渲染）
            items.reversed().forEachIndexed { index, item ->
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = slideInVertically { it / 2 } + fadeIn(),
                    exit = slideOutVertically { it / 2 } + fadeOut()
                ) {
                    MiniFAB(item = item)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // 主 FAB
            FloatingActionButton(
                onClick = onToggle
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (isExpanded) 45f else 0f
                    }
                )
            }
        }
    }
}

@Composable
private fun MiniFAB(item: SpeedDialItem) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = item.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
        )
        SmallFloatingActionButton(
            onClick = item.onClick
        ) {
            item.icon()
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(
        androidx.compose.foundation.clickable(
            indication = null,
            interactionSource = null,
            onClick = onClick
        )
    )
```

注意：遮罩和 dismiss 需要在父级 Box 中处理（BookListScreen 中），SpeedDialFAB 本身只负责 FAB + Mini FAB 的渲染。实际实现时需要确保 dismiss 的点击区域不遮挡 FAB 区域。具体布局策略：在 BookListScreen 中，用 `Box` 包裹整个内容，FAB 区域 `align(BottomEnd)`，展开时在 FAB 下方添加一个 `Box(modifier = Modifier.fillMaxSize().clickable { onDismiss })` 并用 `pointerInput` 确保不拦截 FAB 点击。

- [ ] **步骤 2：Commit**

```bash
git add ui/components/SpeedDialFAB.kt
git commit -m "feat: add SpeedDialFAB composable component"
```

---

## 任务 7：FolderCard 组件

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/ui/components/FolderCard.kt`

- [ ] **步骤 1：创建 FolderCard + FolderListItem**

创建 `ui/components/FolderCard.kt`：

```kotlin
package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ebookreader.simplebook.domain.model.Folder

@Composable
fun FolderCard(
    folder: Folder,
    bookCount: Int,
    bookCountText: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(
                        horizontal = if (compact) 6.dp else 16.dp,
                        vertical = if (compact) 10.dp else 20.dp
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 28.dp else 64.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))
                    Text(
                        text = folder.name,
                        style = if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = bookCountText,
                        style = if (compact) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
fun FolderListItem(
    folder: Folder,
    bookCount: Int,
    bookCountText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = bookCountText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add ui/components/FolderCard.kt
git commit -m "feat: add FolderCard and FolderListItem composable components"
```

---

## 任务 8：AdaptiveBookGrid 重构支持 ShelfItem

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/components/AdaptiveBookGrid.kt`

- [ ] **步骤 1：重构 AdaptiveBookGrid**

将 `AdaptiveBookGrid.kt` 的参数从 `books: List<Book>` 改为 `items: List<ShelfItem>`：

```kotlin
@Composable
fun AdaptiveBookGrid(
    items: List<ShelfItem>,
    layoutMode: LayoutMode = LayoutMode.LARGE_GRID,
    windowWidthSizeClass: WindowWidthSizeClass,
    onBookClick: (Book) -> Unit,
    onBookLongClick: (Book) -> Unit,
    onFolderClick: (Folder) -> Unit,
    unknownAuthorText: String = "未知",
    bookCountText: (Int) -> String = { "共 $it 本" },
    modifier: Modifier = Modifier
) {
    val isCompact = windowWidthSizeClass == WindowWidthSizeClass.Compact

    if (layoutMode == LayoutMode.LIST) {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { when (it) {
                is ShelfItem.BookItem -> "book_${it.book.uuid}"
                is ShelfItem.FolderItem -> "folder_${it.folder.uuid}"
            }}) { item ->
                when (item) {
                    is ShelfItem.BookItem -> BookListItem(
                        book = item.book,
                        onClick = { onBookClick(item.book) },
                        onLongClick = { onBookLongClick(item.book) },
                        unknownAuthorText = unknownAuthorText,
                        percentage = item.progress
                    )
                    is ShelfItem.FolderItem -> FolderListItem(
                        folder = item.folder,
                        bookCount = item.bookCount,
                        bookCountText = bookCountText(item.bookCount),
                        onClick = { onFolderClick(item.folder) }
                    )
                }
            }
        }
    } else {
        val columns = when {
            layoutMode == LayoutMode.LARGE_GRID && isCompact -> 2
            layoutMode == LayoutMode.LARGE_GRID -> 4
            layoutMode == LayoutMode.SMALL_GRID && isCompact -> 3
            else -> 6
        }
        val isSmall = layoutMode == LayoutMode.SMALL_GRID

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = if (isSmall) 12.dp else 16.dp,
                vertical = if (isSmall) 8.dp else 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (isSmall) 8.dp else 12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSmall) 10.dp else 16.dp)
        ) {
            items(items, key = { when (it) {
                is ShelfItem.BookItem -> "book_${it.book.uuid}"
                is ShelfItem.FolderItem -> "folder_${it.folder.uuid}"
            }}) { item ->
                when (item) {
                    is ShelfItem.BookItem -> BookCard(
                        book = item.book,
                        onClick = { onBookClick(item.book) },
                        onLongClick = { onBookLongClick(item.book) },
                        unknownAuthorText = unknownAuthorText,
                        percentage = item.progress,
                        compact = isSmall
                    )
                    is ShelfItem.FolderItem -> FolderCard(
                        folder = item.folder,
                        bookCount = item.bookCount,
                        bookCountText = bookCountText(item.bookCount),
                        onClick = { onFolderClick(item.folder) },
                        compact = isSmall
                    )
                }
            }
        }
    }
}
```

- [ ] **步骤 2：Commit**

```bash
git add ui/components/AdaptiveBookGrid.kt
git commit -m "refactor: AdaptiveBookGrid to accept ShelfItem list with folder support"
```

---

## 任务 9：AppStrings 本地化更新

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/model/AppStrings.kt`

- [ ] **步骤 1：添加新字符串字段**

在 `AppStrings` data class 中，在 `layoutList` 后面添加：

```kotlin
// Folder
val createFolder: String,
val folderName: String,
val moveToFolder: String,
val moveToShelf: String,
val moveBackToShelf: String,
val noFolders: String,
val bookCount: (count: Int) -> String,
// Sort
val sortByLastRead: String,
val sortByName: String,
// Speed Dial
val importBooks: String,
val newFolder: String,
val sort: String,
```

- [ ] **步骤 2：更新中文字符串**

在 `else` 分支的 AppStrings 中添加：

```kotlin
createFolder = "新建文件夹",
folderName = "文件夹名称",
moveToFolder = "移入文件夹",
moveToShelf = "移回主书架",
moveBackToShelf = "移回主书架",
noFolders = "暂无文件夹，请先创建",
bookCount = { count -> "共 $count 本" },
sortByLastRead = "按最后阅读时间",
sortByName = "按名称",
importBooks = "导入书籍",
newFolder = "新建文件夹",
sort = "排序",
```

- [ ] **步骤 3：更新英文字符串**

在 `"en"` 分支的 AppStrings 中添加：

```kotlin
createFolder = "New Folder",
folderName = "Folder name",
moveToFolder = "Move to Folder",
moveToShelf = "Move to Shelf",
moveBackToShelf = "Move to Shelf",
noFolders = "No folders yet. Create one first.",
bookCount = { count -> "$count books" },
sortByLastRead = "By Last Read",
sortByName = "By Name",
importBooks = "Import Books",
newFolder = "New Folder",
sort = "Sort",
```

- [ ] **步骤 4：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 5：Commit**

```bash
git add domain/model/AppStrings.kt ui/components/FolderCard.kt
git commit -m "feat: add localization strings for folder and sort features"
```

---

## 任务 10：BookListViewModel 重构（含排序逻辑）

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/booklist/BookListViewModel.kt`

- [ ] **步骤 1：完整重写 BookListViewModel**

关键设计决策：
- 使用独立 `MutableStateFlow` 收集各数据源（`getShelfBooks`、`getBooksInFolder` 都是 `Flow`，不能在 `combine` lambda 中调用）
- 文件夹的 `bookCount` 在 `allFolders` 收集时一并异步获取
- 排序逻辑直接在 `currentItems` 的 `combine` 中应用，根据 `settings.sortOrder` 动态排序

```kotlin
package com.ebookreader.simplebook.ui.booklist

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.Folder
import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ShelfItem
import com.ebookreader.simplebook.domain.model.SortOrder
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.FileImportService
import com.ebookreader.simplebook.domain.service.FolderService
import com.ebookreader.simplebook.domain.service.ReadingService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookService: BookService,
    private val folderService: FolderService,
    private val fileImportService: FileImportService,
    private val settingsDataStore: SettingsDataStore,
    private val readingService: ReadingService
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = settingsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReaderSettings())

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _currentFolderName = MutableStateFlow<String?>(null)
    val currentFolderName: StateFlow<String?> = _currentFolderName.asStateFlow()

    private val _isSpeedDialExpanded = MutableStateFlow(false)
    val isSpeedDialExpanded: StateFlow<Boolean> = _isSpeedDialExpanded.asStateFlow()

    private val _bookProgress = MutableStateFlow<Map<String, Double>>(emptyMap())
    val bookProgress: StateFlow<Map<String, Double>> = _bookProgress.asStateFlow()

    private val _shelfBooks = MutableStateFlow<List<Book>>(emptyList())
    private val _foldersWithCount = MutableStateFlow<List<Pair<Folder, Int>>>(emptyList())
    private val _folderBooks = MutableStateFlow<List<Book>>(emptyList())

    private val sortOrderFlow = settingsDataStore.settings
        .map { it.sortOrder }
        .distinctUntilChanged()

    val currentItems: StateFlow<List<ShelfItem>> = combine(
        _currentFolderId,
        _shelfBooks,
        _foldersWithCount,
        _folderBooks,
        _bookProgress,
        sortOrderFlow
    ) { folderId, shelfBooks, foldersWithCount, folderBooks, progressMap, sortOrder ->
        val sorted: (List<ShelfItem.BookItem>) -> List<ShelfItem.BookItem> = { items ->
            when (sortOrder) {
                SortOrder.LAST_READ -> items.sortedByDescending { it.book.lastReadAt ?: 0L }
                SortOrder.NAME -> items.sortedBy { it.book.title.lowercase() }
            }
        }

        if (folderId != null) {
            sorted(folderBooks.map { book ->
                ShelfItem.BookItem(book, progressMap[book.uuid] ?: 0.0)
            })
        } else {
            val folderItems = foldersWithCount.map { (folder, count) ->
                ShelfItem.FolderItem(folder, count)
            }
            val bookItems = sorted(shelfBooks.map { book ->
                ShelfItem.BookItem(book, progressMap[book.uuid] ?: 0.0)
            })
            folderItems + bookItems
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            bookService.getShelfBooks().collect { _shelfBooks.value = it }
        }
        viewModelScope.launch {
            folderService.getAllFolders().collect { folders ->
                _foldersWithCount.value = folders.map { folder ->
                    folder to folderService.getBookCountInFolder(folder.uuid)
                }
            }
        }
        viewModelScope.launch {
            _currentFolderId.collect { folderId ->
                if (folderId != null) {
                    bookService.getBooksInFolder(folderId).collect { books ->
                        _folderBooks.value = books
                    }
                } else {
                    _folderBooks.value = emptyList()
                }
            }
        }
        viewModelScope.launch {
            bookService.getAllBooks().collect { bookList ->
                val progressMap = mutableMapOf<String, Double>()
                for (book in bookList) {
                    val progress = readingService.loadProgress(book.uuid)
                    if (progress != null && progress.percentage > 0.0) {
                        progressMap[book.uuid] = progress.percentage
                    }
                }
                _bookProgress.value = progressMap
            }
        }
    }

    fun toggleSpeedDial() {
        _isSpeedDialExpanded.value = !_isSpeedDialExpanded.value
    }

    fun dismissSpeedDial() {
        _isSpeedDialExpanded.value = false
    }

    fun createImportIntent(): Intent = fileImportService.createImportIntent()

    fun importFromUris(uris: List<Uri>) {
        viewModelScope.launch {
            fileImportService.importFromUris(uris)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookService.softDeleteBook(book.uuid)
        }
    }

    fun updateLayoutMode(layoutMode: LayoutMode) {
        viewModelScope.launch {
            settingsDataStore.updateLayoutMode(layoutMode)
        }
    }

    fun updateSortOrder(sortOrder: SortOrder) {
        viewModelScope.launch {
            settingsDataStore.updateSortOrder(sortOrder)
        }
    }

    fun enterFolder(folderId: String, folderName: String) {
        _currentFolderId.value = folderId
        _currentFolderName.value = folderName
    }

    fun exitFolder() {
        _currentFolderId.value = null
        _currentFolderName.value = null
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            folderService.createFolder(name)
        }
    }

    fun moveBookToFolder(bookUuid: String, folderId: String?) {
        viewModelScope.launch {
            bookService.moveBookToFolder(bookUuid, folderId)
        }
    }

    val allFoldersForDialog: StateFlow<List<Pair<Folder, Int>>> = _foldersWithCount
}
```

- [ ] **步骤 2：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 3：Commit**

```bash
git add ui/booklist/BookListViewModel.kt
git commit -m "refactor: BookListViewModel with ShelfItem, folder navigation, sort, and speed dial state"
```

---

## 任务 11：BookListScreen 完整重构

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/ui/booklist/BookListScreen.kt`

这是最复杂的任务，将所有 UI 变更整合到一个 Screen 中。

- [ ] **步骤 1：重写 BookListScreen**

关键变更点：
1. 用 `SpeedDialFAB` 替换原 FAB
2. 顶栏根据 `currentFolderId` 显示返回箭头和文件夹名
3. 新增新建文件夹 Dialog
4. 新增排序 DropdownMenu
5. 长按书籍弹出的对话框改为移入文件夹选项
6. 用 `currentItems` 替代 `books`

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookListScreen(
    windowWidthSizeClass: WindowWidthSizeClass,
    onBookClick: (Book) -> Unit,
    onSyncClick: (() -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: BookListViewModel = hiltViewModel(),
    syncViewModel: SyncViewModel = hiltViewModel()
) {
    val currentItems by viewModel.currentItems.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentFolderId by viewModel.currentFolderId.collectAsState()
    val currentFolderName by viewModel.currentFolderName.collectAsState()
    val isSpeedDialExpanded by viewModel.isSpeedDialExpanded.collectAsState()
    val strings = remember(settings.language) { getStrings(settings.language) }

    // Dialog 状态
    var showLayoutMenu by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var longPressedBook by remember { mutableStateOf<Book?>(null) }

    val syncStatus by syncViewModel.syncStatus.collectAsState()
    val isSignedIn = syncViewModel.isSignedIn
    val isSyncing = syncStatus is SyncStatus.Syncing
    val lastSyncedAt by syncViewModel.lastSyncedAt.collectAsState()

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importFromUris(uris)
        }
        viewModel.dismissSpeedDial()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (currentFolderId != null) {
                        // 文件夹视图：返回箭头 + 文件夹名
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.exitFolder() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                            }
                            Text(currentFolderName ?: "")
                        }
                    } else {
                        // 主书架：原有布局切换
                        Box {
                            Row(
                                modifier = Modifier.clickable { showLayoutMenu = true },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(strings.navBooks)
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            // 布局菜单（保持原有逻辑）
                            DropdownMenu(
                                expanded = showLayoutMenu,
                                onDismissRequest = { showLayoutMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(strings.layoutLargeGrid) },
                                    onClick = {
                                        viewModel.updateLayoutMode(LayoutMode.LARGE_GRID)
                                        showLayoutMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.GridView, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.layoutSmallGrid) },
                                    onClick = {
                                        viewModel.updateLayoutMode(LayoutMode.SMALL_GRID)
                                        showLayoutMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.ViewModule, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(strings.layoutList) },
                                    onClick = {
                                        viewModel.updateLayoutMode(LayoutMode.LIST)
                                        showLayoutMenu = false
                                    },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Outlined.ViewList, contentDescription = null)
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    // 同步图标（保持原有逻辑）
                    if (isSignedIn) {
                        SyncTimeLabel(lastSyncedAt = lastSyncedAt, isSyncing = isSyncing)
                    }
                    SyncStatusIcon(
                        isSyncing = isSyncing,
                        isSignedIn = isSignedIn,
                        onClick = {
                            if (isSignedIn) {
                                syncViewModel.syncNow()
                                onSyncClick?.invoke()
                            } else {
                                onNavigateToSettings?.invoke()
                            }
                        }
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (currentItems.isEmpty()) {
                Text(
                    text = strings.noContent,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                AdaptiveBookGrid(
                    items = currentItems,
                    layoutMode = settings.layoutMode,
                    windowWidthSizeClass = windowWidthSizeClass,
                    onBookClick = onBookClick,
                    onBookLongClick = { book ->
                        longPressedBook = book
                    },
                    onFolderClick = { folder ->
                        viewModel.enterFolder(folder.uuid, folder.name)
                    },
                    unknownAuthorText = strings.unknownAuthor,
                    bookProgress = emptyMap() // progress 已在 ShelfItem 中
                )
            }

            // Speed Dial FAB
            SpeedDialFAB(
                items = listOf(
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        label = strings.importBooks,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            importLauncher.launch(arrayOf("application/epub+zip", "text/plain"))
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                        label = strings.newFolder,
                        onClick = {
                            viewModel.dismissSpeedDial()
                            showNewFolderDialog = true
                        }
                    ),
                    SpeedDialItem(
                        icon = { Icon(Icons.Default.Sort, contentDescription = null) },
                        label = strings.sort,
                        onClick = {
                            showSortMenu = true
                        }
                    )
                ),
                isExpanded = isSpeedDialExpanded,
                onToggle = { viewModel.toggleSpeedDial() },
                onDismiss = { viewModel.dismissSpeedDial() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            )

            // 排序下拉菜单
            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = {
                    showSortMenu = false
                    viewModel.dismissSpeedDial()
                }
            ) {
                DropdownMenuItem(
                    text = { Text(strings.sortByLastRead) },
                    onClick = {
                        viewModel.updateSortOrder(SortOrder.LAST_READ)
                        showSortMenu = false
                        viewModel.dismissSpeedDial()
                    }
                )
                DropdownMenuItem(
                    text = { Text(strings.sortByName) },
                    onClick = {
                        viewModel.updateSortOrder(SortOrder.NAME)
                        showSortMenu = false
                        viewModel.dismissSpeedDial()
                    }
                )
            }
        }
    }

    // 长按书籍对话框
    longPressedBook?.let { book ->
        AlertDialog(
            onDismissRequest = { longPressedBook = null },
            title = { Text(strings.moveToFolder) },
            text = {
                val folders by viewModel.allFoldersForDialog.collectAsState()
                if (folders.isEmpty()) {
                    Text(strings.noFolders)
                } else {
                    LazyColumn {
                        // 文件夹视图内：显示"移回主书架"选项
                        if (currentFolderId != null) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.moveBookToFolder(book.uuid, null)
                                            longPressedBook = null
                                        }
                                        .padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Outlined.Home,
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(strings.moveBackToShelf)
                                }
                            }
                        }
                        items(folders) { (folder, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.moveBookToFolder(book.uuid, folder.uuid)
                                        longPressedBook = null
                                    }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column {
                                    Text(folder.name)
                                    Text(
                                        text = strings.bookCount(count),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { longPressedBook = null }) {
                    Text(strings.cancel)
                }
            }
        )
    }

    // 新建文件夹对话框
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewFolderDialog = false
                newFolderName = ""
            },
            title = { Text(strings.createFolder) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(strings.folderName) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.createFolder(newFolderName.trim())
                            newFolderName = ""
                        }
                        showNewFolderDialog = false
                    },
                    enabled = newFolderName.isNotBlank()
                ) {
                    Text(strings.save)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewFolderDialog = false
                    newFolderName = ""
                }) {
                    Text(strings.cancel)
                }
            }
        )
    }
}
```

需要额外 import：
```kotlin
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import com.ebookreader.simplebook.domain.model.SortOrder
import com.ebookreader.simplebook.ui.components.SpeedDialFAB
import com.ebookreader.simplebook.ui.components.SpeedDialItem
```

- [ ] **步骤 2：编译验证**

运行：`./gradlew assembleDebug`
预期：编译通过，修复任何 import 或类型错误

- [ ] **步骤 3：Commit**

```bash
git add ui/booklist/BookListScreen.kt
git commit -m "feat: refactor BookListScreen with SpeedDialFAB, folder navigation, sort menu, and move-to-folder dialog"
```

---

## 任务 12：Folder 同步集成

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt`（或等效的同步服务文件）

同步服务的实现需要参考现有的 Book 同步模式。核心变更：

- [ ] **步骤 1：扩展 SyncService 支持 Folder 实体**

参考现有 SyncService 中 Book 的 LWW 同步模式，添加 Folder 的同步逻辑：

a) 在 `GoogleDriveClient` 中添加文件夹相关的上传/下载方法（或复用现有的 JSON 文件上传方法）

b) 在 SyncService 中：
- 上传时：将 Folder 列表序列化为 JSON 上传到 `folder_sync.json`
- 下载时：解析远端 `folder_sync.json`，与本地 Folder 进行 LWW merge
- Book 的 `folderId` 字段随 Book 实体同步，无需额外处理

c) LWW merge 逻辑（与 Book 一致）：
```kotlin
// 远端 folder.updatedAt > 本地 folder.updatedAt → 使用远端
// 否则 → 保留本地
```

- [ ] **步骤 2：编译验证 + 手动同步测试**

运行：`./gradlew assembleDebug`
预期：编译通过

- [ ] **步骤 3：Commit**

```bash
git add domain/service/SyncService.kt domain/service/GoogleDriveClient.kt
git commit -m "feat: add Folder entity sync with LWW merge to Google Drive"
```

---

## 任务 13：最终集成验证

- [ ] **步骤 1：全量编译**

运行：`./gradlew assembleDebug`
预期：BUILD SUCCESSFUL

- [ ] **步骤 2：在设备/模拟器上手动测试**

验证以下场景：
1. Speed Dial FAB 展开/收起动画流畅
2. 导入书籍功能正常
3. 新建文件夹 → 文件夹卡片出现在书架
4. 点击文件夹 → 进入二级视图，显示文件夹内书籍
5. 返回箭头回到主书架
6. 长按书籍 → 移入文件夹对话框
7. 文件夹内长按书籍 → 显示"移回主书架"选项
8. 排序切换正常（按最后阅读 / 按名称）
9. 排序选择持久化，重启 App 保持
10. 大/小网格/列表三种布局下文件夹卡片渲染正确
11. 手机和平板自适应布局正常

- [ ] **步骤 3：最终 Commit**

```bash
git add -A
git commit -m "chore: final integration cleanup for Speed Dial FAB, folders, and sort"
```
