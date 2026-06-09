# 增量同步优化 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将全量同步改为增量同步，日常同步（少量变更）从分钟级降到秒级。

**架构：** Push 阶段通过新增的 `getDirtyBooks()` SQL 查询，一次性检出所有有未同步变更的书（含注解脏数据），只 push 这些书。Pull 阶段利用 Google Drive 文件的 `modifiedTime` 字段过滤，只下载比本地 `lastSyncedAt` 更新的远端书。merge/download 逻辑完全不动。

**技术栈：** Kotlin, Room DAO, Google Drive API v3, Gson, Coroutines

**不改动：** merge 逻辑、downloadBookFromRemote、syncFolders、数据库 schema、注解 Service 层。

---

## 文件变更清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `data/local/dao/BookDao.kt` | 修改 | 新增 `getDirtyBooks()` SQL 查询 |
| `data/repository/BookRepository.kt` | 修改 | 接口新增 `getDirtyBooks()` |
| `data/repository/BookRepositoryImpl.kt` | 修改 | 实现 `getDirtyBooks()` |
| `data/remote/GoogleDriveClient.kt` | 修改 | `listFilesInFolder` 返回值加 `modifiedTime` |
| `domain/service/SyncService.kt` | 修改 | pushToRemote 用脏查询；pullFromRemote 加 modifiedTime 过滤 |
| `domain/model/FolderInfo.kt` | 创建 | `listFilesInFolder` 返回值的数据类（替代 Pair/Triple） |

路径前缀：`app/src/main/java/com/ebookreader/simplebook/`

---

## 关键约束（执行者必读）

1. **注解操作不更新 book.updatedAt** — 书签/高亮/笔记的增删只更新自身 updatedAt，不改 Book。所以脏检测必须包含子表查询。
2. **Pull 先于 Push** — `syncAll()` 中 `pullFromRemote()` → `pushToRemote()` 的顺序不能变。
3. **lastSyncedAt == null 意味着从未同步** — 这类书必须全量 push，不能跳过。
4. **driveFileId == null 意味着书文件未上传** — 这类书必须 push（含文件上传）。
5. **已删除的书如果 updatedAt > lastSyncedAt 必须推送** — 删除信号需传播到远端。
6. **Pull 的 modifiedTime 比较加 60 秒安全余量** — 防本地时钟与 Drive 服务器时钟偏差导致跳过远端变更。
7. **本地不存在的 UUID（新书）不能被 Pull 跳过** — `localBook == null` 时必须走 downloadBookFromRemote。

---

### 任务 1：创建 FolderInfo 数据类

**文件：**
- 创建：`app/src/main/java/com/ebookreader/simplebook/domain/model/FolderInfo.kt`

`listFilesInFolder` 当前返回 `List<Pair<String, String>>`（name, id）。Pull 需要额外的 `modifiedTime` 字段。用数据类替代 Pair/Triple 提高可读性。

- [ ] **步骤 1：创建数据类文件**

```kotlin
package com.ebookreader.simplebook.domain.model

data class FolderInfo(
    val name: String,
    val id: String,
    val modifiedTime: String? = null
)
```

- [ ] **步骤 2：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/model/FolderInfo.kt
git commit -m "feat: add FolderInfo data class for incremental sync"
```

---

### 任务 2：修改 GoogleDriveClient.listFilesInFolder 返回 modifiedTime

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/remote/GoogleDriveClient.kt:129-138`

**当前代码：**

```kotlin
suspend fun listFilesInFolder(folderId: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
    val drive = drive ?: return@withContext emptyList()
    val query = "'$folderId' in parents and trashed=false"
    val result: FileList = drive.files().list()
        .setSpaces("appDataFolder")
        .setQ(query)
        .setFields("files(id, name)")
        .execute()
    result.files.map { it.name to it.id }
}
```

- [ ] **步骤 1：修改 listFilesInFolder 返回值类型和实现**

将返回类型从 `List<Pair<String, String>>` 改为 `List<FolderInfo>`。在 `setFields` 中加上 `modifiedTime`。

```kotlin
suspend fun listFilesInFolder(folderId: String): List<FolderInfo> = withContext(Dispatchers.IO) {
    val drive = drive ?: return@withContext emptyList()
    val query = "'$folderId' in parents and trashed=false"
    val result: FileList = drive.files().list()
        .setSpaces("appDataFolder")
        .setQ(query)
        .setFields("files(id, name, modifiedTime)")
        .execute()
    result.files.map { file ->
        FolderInfo(
            name = file.name,
            id = file.id,
            modifiedTime = file.modifiedTime?.toStringRfc3339()
        )
    }
}
```

注意：`file.modifiedTime` 是 `com.google.api.client.util.DateTime` 类型，`toStringRfc3339()` 返回 ISO 8601 字符串。

- [ ] **步骤 2：修复 SyncService 中 listFilesInFolder 的调用点**

搜索 SyncService.kt 中所有 `listFilesInFolder` 的调用，将解构从 `Pair` 适配为 `FolderInfo`。

**SyncService.kt pullFromRemote（约第 171-173 行）：**

当前：
```kotlin
val remoteFolders = driveClient.listFilesInFolder(appFolderId)
remoteFolders.forEach { (name, id) ->
    Log.d(TAG, "pullFromRemote: found remote folder: name=$name, id=$id")
}
for ((folderName, folderId) in remoteFolders) {
```

改为：
```kotlin
val remoteFolders = driveClient.listFilesInFolder(appFolderId)
remoteFolders.forEach { fi ->
    Log.d(TAG, "pullFromRemote: found remote folder: name=${fi.name}, id=${fi.id}, modifiedTime=${fi.modifiedTime}")
}
for (fi in remoteFolders) {
    val folderName = fi.name
    val folderId = fi.id
    val folderModifiedTime = fi.modifiedTime
```

**SyncService.kt downloadBookFromRemote（约第 833 行）：**

当前：
```kotlin
val filesInFolder = driveClient.listFilesInFolder(folderId)
val bookFileEntry = filesInFolder.firstOrNull { (name, _) ->
    !name.startsWith("metadata") && (name.endsWith(".epub") || name.endsWith(".txt"))
}
```

改为：
```kotlin
val filesInFolder = driveClient.listFilesInFolder(folderId)
val bookFileEntry = filesInFolder.firstOrNull { fi ->
    !fi.name.startsWith("metadata") && (fi.name.endsWith(".epub") || fi.name.endsWith(".txt"))
}
if (bookFileEntry == null) {
    Log.w(TAG, "downloadBookFromRemote: no book file found in folder $folderName")
    return
}
val extension = bookFileEntry.name.substringAfterLast('.').lowercase()
```

同时需要更新 `downloadFileTo` 的调用：
```kotlin
driveClient.downloadFileTo(bookFileEntry.id, localFile)
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/data/remote/GoogleDriveClient.kt app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt
git commit -m "refactor: listFilesInFolder returns FolderInfo with modifiedTime"
```

---

### 任务 3：新增 getDirtyBooks DAO 查询

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/local/dao/BookDao.kt`

这是增量 Push 的核心。一次 SQL 查出所有需要 push 的书。

**脏书的判定条件（OR 关系，命中任一即脏）：**
1. `lastSyncedAt IS NULL` — 从未同步
2. `driveFileId IS NULL` — 书文件未上传到 Drive
3. `updatedAt > lastSyncedAt` — 书本身有变更（标题、作者、删除、移动等）
4. 子表有变更：reading_progress / bookmarks / highlights / notes 中存在 `updatedAt > book.lastSyncedAt` 的记录

- [ ] **步骤 1：在 BookDao 中添加查询方法**

在 `BookDao.kt` 的 `getBooksInFolder` 方法之后添加：

```kotlin
@Query("""
    SELECT DISTINCT b.* FROM books b
    WHERE b.lastSyncedAt IS NULL
       OR b.driveFileId IS NULL
       OR b.updatedAt > b.lastSyncedAt
       OR EXISTS (SELECT 1 FROM reading_progress rp WHERE rp.bookUuid = b.uuid AND rp.updatedAt > b.lastSyncedAt)
       OR EXISTS (SELECT 1 FROM bookmarks bm WHERE bm.bookUuid = b.uuid AND bm.updatedAt > b.lastSyncedAt)
       OR EXISTS (SELECT 1 FROM highlights hl WHERE hl.bookUuid = b.uuid AND hl.updatedAt > b.lastSyncedAt)
       OR EXISTS (SELECT 1 FROM notes nt WHERE nt.bookUuid = b.uuid AND nt.updatedAt > b.lastSyncedAt)
    """)
suspend fun getDirtyBooks(): List<BookEntity>
```

使用 `EXISTS` 子查询而非 `UNION` — 语义更清晰且对每本书独立检查各自的 `lastSyncedAt`。

- [ ] **步骤 2：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/data/local/dao/BookDao.kt
git commit -m "feat: add getDirtyBooks DAO query for incremental push"
```

---

### 任务 4：Repository 层透传 getDirtyBooks

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/repository/BookRepository.kt`
- 修改：`app/src/main/java/com/ebookreader/simplebook/data/repository/BookRepositoryImpl.kt`

- [ ] **步骤 1：在 BookRepository 接口添加方法**

在 `getAllBooksIncludingDeleted()` 声明之后添加：

```kotlin
suspend fun getDirtyBooks(): List<Book>
```

- [ ] **步骤 2：在 BookRepositoryImpl 添加实现**

在 `getAllBooksIncludingDeleted()` 实现之后添加：

```kotlin
override suspend fun getDirtyBooks(): List<Book> =
    bookDao.getDirtyBooks().map { it.toDomain() }
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/data/repository/BookRepository.kt app/src/main/java/com/ebookreader/simplebook/data/repository/BookRepositoryImpl.kt
git commit -m "feat: expose getDirtyBooks through repository layer"
```

---

### 任务 5：修改 pushToRemote 使用脏查询

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt:110-165`

**当前代码（第 113 行）：**

```kotlin
val books = bookRepository.getAllBooksIncludingDeleted()
```

- [ ] **步骤 1：替换为脏查询**

将第 113 行：

```kotlin
val books = bookRepository.getAllBooksIncludingDeleted()
```

改为：

```kotlin
val books = bookRepository.getDirtyBooks()
```

仅此一行改动。后续的 `for (book in books)` 循环及所有上传逻辑完全不变。

**安全性论证：**
- `getDirtyBooks()` 包含 `lastSyncedAt IS NULL` → 新书必推
- `getDirtyBooks()` 包含 `driveFileId IS NULL` → 文件未上传必推
- `getDirtyBooks()` 包含 `updatedAt > lastSyncedAt` → 删除（softDeleteBook 更新 updatedAt）必推
- `getDirtyBooks()` 的 EXISTS 子查询 → 注解变更必推
- 已同步且无变更的书不在结果集中 → 正确跳过

- [ ] **步骤 2：更新日志行**

将第 114 行的日志：

```kotlin
Log.d(TAG, "pushToRemote: appFolderId=$appFolderId, books=${books.size}")
```

改为：

```kotlin
Log.d(TAG, "pushToRemote: appFolderId=$appFolderId, dirtyBooks=${books.size}")
```

- [ ] **步骤 3：编译验证**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 4：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt
git commit -m "perf: use incremental push — only sync dirty books"
```

---

### 任务 6：修改 pullFromRemote 加入 modifiedTime 过滤

**文件：**
- 修改：`app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt:169-225`

在 for 循环内部、`if (!folderName.startsWith("book_")) continue` 之后、`findFileInFolder` 之前，插入增量过滤逻辑。

**当前代码结构（第 177-186 行）：**

```kotlin
for ((folderName, folderId) in remoteFolders) {
    if (!folderName.startsWith("book_")) continue
    Log.d(TAG, "pullFromRemote: processing folder=$folderName, folderId=$folderId")

    val metadataFileId = driveClient.findFileInFolder(folderId, "metadata.json")
    if (metadataFileId == null) {
```

由于任务 2 已将 for 循环改为 `for (fi in remoteFolders)`，这里在此基础上继续修改。

- [ ] **步骤 1：在 pullFromRemote 的 for 循环中插入增量过滤**

在 `Log.d(TAG, "pullFromRemote: processing folder=...")` 之后、`val metadataFileId = driveClient.findFileInFolder(...)` 之前，插入以下代码：

```kotlin
val bookUuid = fi.name.removePrefix("book_")
val localBook = bookRepository.getBookByUuid(bookUuid)

// 增量 Pull：如果本地存在且远端未修改，跳过
if (localBook != null && localBook.lastSyncedAt != null && fi.modifiedTime != null) {
    val remoteModifiedMs = parseModifiedTime(fi.modifiedTime)
    // 60 秒安全余量：防止本地时钟与 Drive 服务器时钟偏差
    if (remoteModifiedMs != null && remoteModifiedMs <= localBook.lastSyncedAt!! + 60_000) {
        Log.d(TAG, "pullFromRemote: skipping unchanged folder=$folderName, remoteModified=$remoteModifiedMs, localSyncedAt=${localBook.lastSyncedAt}")
        continue
    }
}
```

注意 `localBook` 和 `bookUuid` 变量：后续现有代码中也有 `val bookUuid = metadata.bookUuid ?: folderName.removePrefix("book_")` 和 `val localBook = bookRepository.getBookByUuid(bookUuid)`。需要将这两个变量的声明提前到过滤逻辑之前，并移除后续的重复声明。

具体地，后续代码（约第 200-211 行）中的：

```kotlin
val bookUuid = metadata.bookUuid ?: folderName.removePrefix("book_")
```

改为直接使用已在前面声明的 `bookUuid`（从 folder name 提取），但保留 metadata.bookUuid 的覆盖逻辑：

```kotlin
val effectiveBookUuid = metadata.bookUuid ?: bookUuid
```

后续所有使用 `bookUuid` 的地方改为使用 `effectiveBookUuid`。

同样地，后续的 `val localBook = bookRepository.getBookByUuid(bookUuid)` 行需要移除（已提前声明）。

- [ ] **步骤 2：添加 parseModifiedTime 辅助方法**

在 SyncService 的 `companion object` 中添加：

```kotlin
private fun parseModifiedTime(isoTime: String): Long? {
    return try {
        com.google.api.client.util.DateTime(isoTime).value
    } catch (e: Exception) {
        Log.w(TAG, "parseModifiedTime: failed to parse '$isoTime'", e)
        null
    }
}
```

- [ ] **步骤 3：完整修改后的 pullFromRemote for 循环**

确保 for 循环内部的完整流程如下（伪代码，标注改动点）：

```
for (fi in remoteFolders) {
    val folderName = fi.name
    val folderId = fi.id

    if (!folderName.startsWith("book_")) continue
    Log.d(TAG, "pullFromRemote: processing folder=$folderName, folderId=$folderId")

    // [新增] 提前计算 bookUuid 和 localBook
    val bookUuid = folderName.removePrefix("book_")
    val localBook = bookRepository.getBookByUuid(bookUuid)

    // [新增] 增量过滤
    if (localBook != null && localBook.lastSyncedAt != null && fi.modifiedTime != null) {
        val remoteModifiedMs = parseModifiedTime(fi.modifiedTime)
        if (remoteModifiedMs != null && remoteModifiedMs <= localBook.lastSyncedAt!! + 60_000) {
            Log.d(...)
            continue
        }
    }

    // 以下原有逻辑不变
    val metadataFileId = driveClient.findFileInFolder(folderId, "metadata.json")
    if (metadataFileId == null) { ... continue }
    val metadataBytes = driveClient.downloadFile(metadataFileId)
    if (metadataBytes == null) { ... continue }
    val metadata = gson.fromJson(...)

    // [改动] 用 effectiveBookUuid 替代原 bookUuid
    val effectiveBookUuid = metadata.bookUuid ?: bookUuid
    if (metadata.bookUuid == null) { ... continue }

    // [改动] 移除原有的 val localBook = ... 行（已提前声明）

    if (localBook == null && !metadata.isDeleted) {
        downloadBookFromRemote(folderId, folderName, metadata)
    } else if (localBook != null) {
        mergeLocalBook(localBook, metadata, folderId)
    } else { ... }
}
```

- [ ] **步骤 4：编译验证**

```bash
./gradlew compileDebugKotlin 2>&1 | tail -5
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 5：Commit**

```bash
git add app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt
git commit -m "perf: incremental pull — skip unchanged remote folders via modifiedTime"
```

---

### 任务 7：端到端验证

**文件：** 无新增文件，使用真机或模拟器验证。

- [ ] **步骤 1：构建 debug APK**

```bash
./gradlew assembleDebug 2>&1 | tail -5
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 2：安装到设备**

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **步骤 3：验证增量 Push**

1. 导入 2 本书（确保之前已同步过）
2. 对其中 1 本添加一个书签
3. 触发同步
4. 检查 Logcat 过滤 `SyncService`：
   - 应看到 `dirtyBooks=1`（只有书签变更的那本被推送，而非 2 本）
   - 不应看到第 2 本书的 `pushToRemote: pushing book=` 日志

```bash
adb logcat -s SyncService | grep -E "dirtyBooks|pushing book"
```

- [ ] **步骤 4：验证增量 Pull**

1. 在另一台设备（或清除本地数据重新登录）执行同步
2. 对第 1 台设备触发同步 → 应看到 Pull 下载了所有书
3. 在第 1 台设备再次同步 → 应看到 Pull 跳过了未变更的书（`skipping unchanged folder`）

```bash
adb logcat -s SyncService | grep -E "skipping unchanged|processing folder"
```

- [ ] **步骤 5：验证新书同步不受影响**

1. 在设备 A 导入一本新书
2. 设备 A 同步 → Push 应推送这本新书
3. 设备 B 同步 → Pull 应下载这本新书
4. 确认两台设备都能看到这本书

- [ ] **步骤 6：验证删除同步不受影响**

1. 在设备 A 删除一本书
2. 设备 A 同步 → Push 应推送删除状态
3. 设备 B 同步 → Pull 应收到删除信号，书从列表消失
4. 设备 B 再次同步 → Push 不应将这本书以非删除状态推送回去（不复活）

---

## 自检

### 规格覆盖度
- 增量 Push 脏检测：任务 3（DAO）+ 任务 4（Repository）+ 任务 5（SyncService）
- 增量 Pull modifiedTime 过滤：任务 2（DriveClient）+ 任务 6（SyncService）
- 新书同步安全：脏查询含 `lastSyncedAt IS NULL` + `driveFileId IS NULL`；Pull 中 `localBook == null` 不走过滤
- 删除同步安全：softDeleteBook 更新 updatedAt → `updatedAt > lastSyncedAt` 命中脏查询；Pull modifiedTime 过滤不跳过已删除远端书

### 占位符扫描
无 TODO / 待定 / 后续实现 / 补充细节。所有步骤包含完整代码。

### 类型一致性
- `FolderInfo` 在任务 1 创建，任务 2 的 `listFilesInFolder` 和 `SyncService` 使用
- `BookDao.getDirtyBooks()` 返回 `List<BookEntity>`，与 `getAllBooksIncludingDeleted()` 返回类型一致
- `BookRepository.getDirtyBooks()` 返回 `List<Book>`，与 `getAllBooksIncludingDeleted()` 返回类型一致
- `parseModifiedTime` 使用 `com.google.api.client.util.DateTime`，与 Drive API 的 `DateTime` 类型一致
