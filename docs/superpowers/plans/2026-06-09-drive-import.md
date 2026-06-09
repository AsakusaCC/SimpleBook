# Drive Import 实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 允许用户从 Google Drive 可见文件夹 `SimpleBook/Import/` 导入 epub/txt 书籍到本地书架。

**架构：** 在现有 SyncService 中新增 `importFromDriveFolder()` 方法，复用 GoogleDriveClient 基础设施但操作用户可见的 Drive 空间。导入成功后删除 Drive 源文件，通过 SharedPreferences 记录已处理文件 ID 防止重复导入。

**技术栈：** Kotlin, Google Drive API v3, Jetpack Compose, Hilt, StateFlow

---

## 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| 修改 | `data/remote/AuthManager.kt` | 增加 `DRIVE_FILE` scope |
| 修改 | `data/remote/GoogleDriveClient.kt` | 新增 4 个用户可见空间操作方法 |
| 修改 | `domain/service/SyncService.kt` | 新增 `importFromDriveFolder()` + 状态流 |
| 修改 | `ui/settings/SettingsViewModel.kt` | 新增导入状态和方法 |
| 修改 | `ui/settings/SettingsScreen.kt` | 新增「从 Drive 导入」按钮 UI |
| 修改 | `domain/model/AppStrings.kt` | 新增导入相关中英文字符串 |

路径前缀均为 `app/src/main/java/com/ebookreader/simplebook/`

---

### 任务 1：AuthManager 增加 DRIVE_FILE scope

**文件：**
- 修改：`data/remote/AuthManager.kt:28`

- [ ] **步骤 1：修改 scope 配置**

将 `AuthManager.kt` 第 28 行：
```kotlin
.requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
```
改为：
```kotlin
.requestScopes(Scope(DriveScopes.DRIVE_APPDATA), Scope(DriveScopes.DRIVE_FILE))
```

- [ ] **步骤 2：更新 GoogleDriveClient 的 credential scope**

将 `GoogleDriveClient.kt` 第 29 行：
```kotlin
context, listOf(DriveScopes.DRIVE_APPDATA)
```
改为：
```kotlin
context, listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE_FILE)
```

- [ ] **步骤 3：Commit**

```bash
git add -A && git commit -m "feat: add DRIVE_FILE scope for user-visible Drive access"
```

---

### 任务 2：GoogleDriveClient 新增用户可见空间方法

**文件：**
- 修改：`data/remote/GoogleDriveClient.kt`

在 `GoogleDriveClient.kt` 文件末尾（`deleteFile` 方法之后，`}` 闭合类之前）添加以下 4 个方法：

- [ ] **步骤 1：添加 `findOrCreateUserFolder` 方法**

在 `deleteFile()` 方法（第 157 行）之后添加：

```kotlin
    // ── User-visible Drive operations ───────────────────────────────

    suspend fun findOrCreateUserFolder(name: String, parentId: String = "root"): String? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val query = "'$parentId' in parents and name='$name' and mimeType='application/vnd.google-apps.folder' and trashed=false"
        val result: FileList = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id)")
            .execute()
        val existing = result.files.firstOrNull()?.id
        if (existing != null) return@withContext existing
        drive.files().create(
            com.google.api.services.drive.model.File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }
        ).setFields("id").execute().id
    }
```

- [ ] **步骤 2：添加 `listUserFiles` 方法**

```kotlin
    data class DriveFileInfo(
        val id: String,
        val name: String,
        val size: Long,
        val mimeType: String
    )

    suspend fun listUserFiles(folderId: String): List<DriveFileInfo> = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext emptyList()
        val query = "'$folderId' in parents and trashed=false"
        val result: FileList = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name, size, mimeType)")
            .execute()
        result.files.map { file ->
            DriveFileInfo(
                id = file.id,
                name = file.name,
                size = file.size ?: 0L,
                mimeType = file.mimeType ?: ""
            )
        }
    }
```

- [ ] **步骤 3：添加 `downloadUserFile` 和 `deleteUserFile` 方法**

```kotlin
    suspend fun downloadUserFile(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        out.toByteArray()
    }

    suspend fun deleteUserFile(fileId: String) = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext
        drive.files().delete(fileId).execute()
    }
```

- [ ] **步骤 4：Commit**

```bash
git add -A && git commit -m "feat: add user-visible Drive folder operations to GoogleDriveClient"
```

---

### 任务 3：SyncService 新增 importFromDriveFolder()

**文件：**
- 修改：`domain/service/SyncService.kt`

- [ ] **步骤 1：添加 ImportStatus 密封类和状态流**

在 `SyncStatus` 密封类（第 43-48 行）之后添加：

```kotlin
sealed class ImportStatus {
    data object Idle : ImportStatus()
    data object Importing : ImportStatus()
    data class Success(val count: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}
```

在 `SyncService` 类中（`_lastSyncedAt` 定义之后，`syncMutex` 之前，约第 71 行）添加：

```kotlin
    private val _importStatus = MutableStateFlow<ImportStatus>(ImportStatus.Idle)
    val importStatus: StateFlow<ImportStatus> = _importStatus.asStateFlow()
```

- [ ] **步骤 2：添加 `importFromDriveFolder()` 方法**

在 `companion object`（第 74-77 行）的 `FOLDERS_FILENAME` 常量之后添加：

```kotlin
        private const val PROCESSED_IMPORT_IDS = "processed_import_ids"
```

在类的末尾（`downloadBookFromRemote` 方法之后，类闭合 `}` 之前）添加：

```kotlin
    // ── Drive Import ────────────────────────────────────────────────

    suspend fun importFromDriveFolder() {
        if (!authManager.isSignedIn) {
            _importStatus.value = ImportStatus.Error("请先登录 Google 账号")
            return
        }

        _importStatus.value = ImportStatus.Importing

        try {
            // 1. Find or create SimpleBook/Import/ folder
            val simpleBookFolderId = driveClient.findOrCreateUserFolder("SimpleBook")
                ?: throw Exception("无法创建 SimpleBook 文件夹")
            val importFolderId = driveClient.findOrCreateUserFolder("Import", simpleBookFolderId)
                ?: throw Exception("无法创建 Import 文件夹")

            // 2. List files in Import folder
            val files = driveClient.listUserFiles(importFolderId)
            Log.d(TAG, "importFromDriveFolder: found ${files.size} files in Import folder")

            // 3. Filter epub/txt files
            val supportedExtensions = setOf("epub", "txt")
            val bookFiles = files.filter { file ->
                val ext = file.name.substringAfterLast('.', "").lowercase()
                ext in supportedExtensions
            }

            if (bookFiles.isEmpty()) {
                _importStatus.value = ImportStatus.Success(0)
                return
            }

            // 4. Load processed IDs
            val processedIds = prefs.getStringSet(PROCESSED_IMPORT_IDS, emptySet())?.toMutableSet()
                ?: mutableSetOf()

            var importedCount = 0
            val booksDir = File(context.filesDir, "books").also { it.mkdirs() }

            for (file in bookFiles) {
                if (file.id in processedIds) {
                    Log.d(TAG, "importFromDriveFolder: skipping already processed file ${file.name}")
                    continue
                }

                try {
                    val extension = file.name.substringAfterLast('.').lowercase()
                    val format = when (extension) {
                        "epub" -> BookFormat.EPUB
                        "txt" -> BookFormat.TXT
                        else -> continue
                    }

                    // Download to local
                    val localFileName = "${UUID.randomUUID()}.$extension"
                    val localFile = File(booksDir, localFileName)
                    val bytes = driveClient.downloadUserFile(file.id)
                    if (bytes == null) {
                        Log.w(TAG, "importFromDriveFolder: failed to download ${file.name}")
                        continue
                    }
                    localFile.writeBytes(bytes)

                    // Parse book info
                    val originalName = file.name.substringBeforeLast('.')
                    val title: String
                    val author: String
                    var coverPath: String? = null

                    when (format) {
                        BookFormat.EPUB -> {
                            val result = epubParser.parse(localFile)
                            title = result.title.ifBlank { originalName }
                            author = result.author
                            coverPath = result.coverPath
                        }
                        BookFormat.TXT -> {
                            val result = txtParser.parse(localFile)
                            title = originalName
                            author = result.author
                        }
                    }

                    // Save to database
                    val book = Book(
                        title = title,
                        author = author,
                        filePath = localFile.absolutePath,
                        format = format,
                        coverPath = coverPath,
                        fileSize = localFile.length()
                    )
                    bookRepository.addBook(book)

                    // Delete source file from Drive
                    driveClient.deleteUserFile(file.id)

                    // Record processed ID
                    processedIds.add(file.id)
                    prefs.edit().putStringSet(PROCESSED_IMPORT_IDS, processedIds).apply()

                    importedCount++
                    Log.d(TAG, "importFromDriveFolder: imported $title")
                } catch (e: Exception) {
                    Log.e(TAG, "importFromDriveFolder: failed to import ${file.name}", e)
                }
            }

            _importStatus.value = ImportStatus.Success(importedCount)
        } catch (e: Exception) {
            Log.e(TAG, "importFromDriveFolder: failed", e)
            _importStatus.value = ImportStatus.Error(e.message ?: "导入失败")
        }
    }
```

注意：此方法需要 `TxtParser` 依赖。检查 SyncService 的构造函数是否已注入 `TxtParser`，如果没有则需要添加。

- [ ] **步骤 3：检查并添加 TxtParser 依赖**

如果 `SyncService` 构造函数中没有 `txtParser: TxtParser`，需要添加。先检查现有注入：

```bash
grep -n "TxtParser" app/src/main/java/com/ebookreader/simplebook/domain/service/SyncService.kt
```

如果没有结果，在构造函数参数中添加 `private val txtParser: TxtParser`（和 `epubParser` 并列）。

- [ ] **步骤 4：Commit**

```bash
git add -A && git commit -m "feat: add importFromDriveFolder to SyncService"
```

---

### 任务 4：SettingsViewModel 新增导入状态和方法

**文件：**
- 修改：`ui/settings/SettingsViewModel.kt`

- [ ] **步骤 1：添加 ImportStatus import**

在文件头部 import 区域添加：

```kotlin
import com.ebookreader.simplebook.domain.service.ImportStatus
```

- [ ] **步骤 2：添加导入状态和方法**

在 `SettingsViewModel` 类中，`_cleanDriveState`（第 85-86 行）之后添加：

```kotlin
    val importStatus: StateFlow<ImportStatus> = syncService.importStatus
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ImportStatus.Idle)
```

在 `dismissCleanResult()` 方法（第 216-218 行）之后添加：

```kotlin
    fun importFromDrive() {
        viewModelScope.launch { syncService.importFromDriveFolder() }
    }

    fun dismissImportResult() {
        // Reset is handled by SyncService, just observe state
    }
```

- [ ] **步骤 3：Commit**

```bash
git add -A && git commit -m "feat: add Drive import state and method to SettingsViewModel"
```

---

### 任务 5：AppStrings 新增导入相关字符串

**文件：**
- 修改：`domain/model/AppStrings.kt`

- [ ] **步骤 1：在 AppStrings data class 中添加字段**

在 `cleanDriveConfirmMessage` 字段（第 67 行）之后添加：

```kotlin
    // Drive Import
    val driveImportTitle: String,
    val driveImportDescription: String,
    val driveImportButton: String,
    val driveImporting: String,
    val driveImportResult: (count: Int) -> String,
    val driveImportEmpty: String
```

- [ ] **步骤 2：在英文字符串块（`"en" ->`）中添加对应值**

在 `cleanDriveConfirmMessage` 赋值之后添加：

```kotlin
        // Drive Import
        driveImportTitle = "Import from Drive",
        driveImportDescription = "Put epub/txt files in Google Drive → SimpleBook → Import folder, then tap the button below.",
        driveImportButton = "Import from Drive",
        driveImporting = "Importing from Drive...",
        driveImportResult = { count -> "Successfully imported $count books" },
        driveImportEmpty = "No new books found in Import folder"
```

- [ ] **步骤 3：在中文字符串块（`else ->`）中添加对应值**

在 `cleanDriveConfirmMessage` 赋值之后添加：

```kotlin
        // Drive Import
        driveImportTitle = "从 Drive 导入",
        driveImportDescription = "将 epub/txt 文件放入 Google Drive → SimpleBook → Import 文件夹，然后点击下方按钮导入。",
        driveImportButton = "从 Drive 导入",
        driveImporting = "正在从 Drive 导入...",
        driveImportResult = { count -> "成功导入 $count 本书籍" },
        driveImportEmpty = "Import 文件夹中没有新书"
```

- [ ] **步骤 4：Commit**

```bash
git add -A && git commit -m "feat: add Drive Import strings for Chinese and English"
```

---

### 任务 6：SettingsScreen 新增「从 Drive 导入」按钮 UI

**文件：**
- 修改：`ui/settings/SettingsScreen.kt`

在「网盘清理」section 之前（`// ── 网盘清理 ──` 注释处，约第 257 行）插入新的 section：

- [ ] **步骤 1：添加 Drive Import UI section**

在 `// ── 网盘清理 ──` 之前插入：

```kotlin
            // ── 从 Drive 导入 ──
            HorizontalDivider()
            SectionHeader(strings.driveImportTitle)
            Text(
                strings.driveImportDescription,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isSignedIn) {
                when (val status = importStatus) {
                    is ImportStatus.Idle -> {
                        OutlinedButton(
                            onClick = { viewModel.importFromDrive() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(strings.driveImportButton)
                        }
                    }
                    is ImportStatus.Importing -> {
                        Text(
                            strings.driveImporting,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    is ImportStatus.Success -> {
                        val msg = if (status.count == 0) strings.driveImportEmpty
                                  else strings.driveImportResult(status.count)
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.importFromDrive() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(strings.driveImportButton)
                        }
                    }
                    is ImportStatus.Error -> {
                        Text(
                            "导入失败: ${status.message}",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        OutlinedButton(
                            onClick = { viewModel.importFromDrive() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(strings.driveImportButton)
                        }
                    }
                }
            }
```

- [ ] **步骤 2：确保 SettingsScreen composable 接收 importStatus 参数**

检查 SettingsScreen 的 composable 函数签名，确认它接收 `importStatus` 参数。如果参数是从 SettingsViewModel 传递的，确保调用处在 `remember` 或参数中正确传递了 `viewModel.importStatus.collectAsState()`。

具体做法：在 SettingsScreen 中找到其他 sync 状态（如 `syncStatus`、`isSignedIn`）的获取方式，用相同方式获取 `importStatus`。

```bash
grep -n "syncStatus\|importStatus" app/src/main/java/com/ebookreader/simplebook/ui/settings/SettingsScreen.kt
```

如果 `syncStatus` 是通过参数传入的，则 `importStatus` 也需要通过参数传入。如果直接从 viewModel 获取，则添加：

```kotlin
val importStatus by viewModel.importStatus.collectAsState()
```

同时添加 import：
```kotlin
import com.ebookreader.simplebook.domain.service.ImportStatus
```

- [ ] **步骤 3：Commit**

```bash
git add -A && git commit -m "feat: add Drive Import button to Settings screen"
```

---

### 任务 7：编译验证

- [ ] **步骤 1：运行编译**

```bash
./gradlew assembleDebug 2>&1 | tail -30
```

预期：BUILD SUCCESSFUL

- [ ] **步骤 2：修复编译错误（如有）**

根据编译输出修复遗漏的 import、参数传递或类型不匹配问题。

- [ ] **步骤 3：最终 Commit**

```bash
git add -A && git commit -m "fix: resolve compilation issues for Drive Import feature"
```
