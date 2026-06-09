# Drive Import 功能设计

## 概述

允许用户在 Google Drive 网页端 / PC 端将 epub / txt 文件放入 `SimpleBook/Import/` 文件夹，然后在 app 中点击「从 Drive 导入」按钮，自动检测、下载并导入到本地书架。

## 背景

当前同步使用 `appDataFolder`（应用私有、不可见目录），用户无法从 PC / 网页端手动放入书籍。本功能在用户可见的 Drive 空间新增一个 Import 文件夹作为"投递口"。

## 用户体验流程

1. 用户在 Drive 网页端看到 `SimpleBook/Import/` 文件夹（app 自动创建）
2. 用户拖入 epub / txt 文件
3. 用户在 app 设置页点击「从 Drive 导入」
4. App 检测新文件 → 下载 → 导入到书架 → 删除 Drive 源文件
5. Import 文件夹清空，等待下次使用

## 技术设计

### 1. 权限变更

**文件：** `AuthManager.kt`

```
现有：DriveScopes.DRIVE_APPDATA
新增：DriveScopes.DRIVE_FILE
```

用户需重新登录一次 Google 账号来授权新 scope。

### 2. GoogleDriveClient 新增方法

**文件：** `GoogleDriveClient.kt`

| 方法 | 参数 | 作用 |
|------|------|------|
| `findOrCreateUserFolder(name, parentId?)` | 文件夹名、父目录 ID（可选，默认 root） | 在用户可见 Drive 空间查找或创建文件夹 |
| `listUserFiles(folderId)` | 文件夹 ID | 列出指定文件夹下的文件（文件名、ID、大小、mimeType） |
| `downloadUserFile(fileId)` | 文件 ID | 下载用户可见空间的文件为 ByteArray |
| `deleteUserFile(fileId)` | 文件 ID | 删除用户可见空间的文件 |

这些方法与现有 `appDataFolder` 方法逻辑一致，区别在于不调用 `setSpaces("appDataFolder")`。

### 3. SyncService 新增 importFromDriveFolder()

**文件：** `SyncService.kt`

```
importFromDriveFolder():
  1. 检查 Google 登录状态
  2. 获取或创建 SimpleBook/ 文件夹（findOrCreateUserFolder）
  3. 获取或创建 SimpleBook/Import/ 文件夹
  4. 列出 Import/ 下所有文件
  5. 过滤出 .epub / .txt 文件
  6. 对每个文件：
     a. 检查 file ID 是否在 SharedPreferences "processed_import_ids" 集合中 → 跳过
     b. 下载到本地 books 目录（复用现有 booksDir 路径）
     c. 解析书籍信息（复用 BookService.importBook 逻辑：EpubParser / TxtParser）
     d. 通过 BookRepository.addBook() 入库
     e. 删除 Drive 上的源文件（deleteUserFile）
     f. 将 file ID 加入 "processed_import_ids" 集合
  7. 返回导入结果（成功数 / 跳过数 / 失败数）
```

**已处理文件 ID 记录：** 存储在 SharedPreferences 的 StringSet `processed_import_ids` 中。即使步骤 6e 删除失败，下次也不会重复导入。

### 4. SyncViewModel 暴露导入状态

**文件：** `SyncViewModel.kt`

新增状态：
- `importStatus: StateFlow<ImportStatus>`（Idle / Importing / Success(count) / Error(message)）
- `importFromDrive()` 方法，调用 SyncService

### 5. UI 变更

**文件：** `SyncComponents.kt`（或设置页同步区域）

在现有同步按钮附近新增「从 Drive 导入」按钮。点击后：
- 按钮变为 loading 状态
- 显示进度信息（"发现 3 本新书"、"正在导入..."、"已导入 3 本"）
- 完成后恢复按钮状态

### 6. 错误处理

| 场景 | 处理 |
|------|------|
| 未登录 Google | 提示"请先登录 Google 账号" |
| Import 文件夹不存在 | 自动创建 |
| 单个文件下载失败 | 跳过该文件，继续处理其余文件，最终汇总报告 |
| 文件格式不是 epub/txt | 跳过 |
| Drive API 网络错误 | 显示错误信息，不删除源文件，可重试 |
| 磁盘空间不足 | 显示错误信息 |

### 7. 不涉及

- 不修改现有 appDataFolder 同步逻辑
- 不做自动后台检测（用户主动触发）
- 不支持子文件夹嵌套（只扫描 Import/ 根目录下的文件）
- 不做增量检测（每次点击都扫描全部文件，通过 processed_import_ids 去重）
