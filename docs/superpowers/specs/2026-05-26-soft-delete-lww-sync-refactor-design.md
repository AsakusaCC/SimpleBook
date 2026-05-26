# 同步机制重构：软删除 + Last-Write-Wins

日期：2026-05-26

## 背景

当前同步机制使用物理删除 + syncVersion 手动冲突解决。在多设备并发场景下（A 设备添加、B 设备删除），会导致数据错乱和"死者复生"问题。需要彻底重构为软删除 + 自动 LWW 合并。

## 核心规则

1. **严禁物理删除** — 云端和客户端去掉所有 DELETE 操作，改用 `is_deleted` 软删除
2. **Last-Write-Wins** — 同 uuid 冲突时，`updated_at` 毫秒级时间戳最大的那条胜出
3. **前端透明过滤** — 渲染时自动过滤 `is_deleted == true`

## 决策记录

| 决策点 | 选择 | 理由 |
|--------|------|------|
| UUID 策略 | uuid 替代 Long id 成为主键 | 架构更干净，跨设备唯一标识 |
| 冲突解决 | 自动 LWW + 合并日志 UI | 减少用户干预，保留可观测性 |
| 远端格式 | 沿用 metadata.json | 最小改动，兼容现有结构 |
| 阅读进度 | 特殊处理取百分比最高 | 符合用户预期，不丢失进度 |

---

## 一、数据模型层

### 1.1 统一基字段

所有同步实体增加三个字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `uuid` | String | 主键，UUID v4 自动生成 |
| `updated_at` | Long | 毫秒级时间戳，每次写入自动更新 |
| `is_deleted` | Boolean | 软删除标记，默认 false |

### 1.2 实体变更

**BookEntity：**
- `id: Long` → `uuid: String`（主键）
- 新增 `updated_at: Long`
- 新增 `is_deleted: Boolean = false`
- 移除 `syncVersion`、`lastSyncedAt`（合并为统一 `lastSyncedAt` 保留在 Book 级别）
- `driveFileId` 保留不变

**ReadingProgressEntity：**
- `id: Long` → `uuid: String`（主键）
- `bookId: Long` → `bookUuid: String`（外键）
- 新增 `updated_at: Long`
- 新增 `is_deleted: Boolean = false`
- 移除 `syncVersion`、`lastSyncedAt`

**BookmarkEntity：**
- `id: Long` → `uuid: String`（主键）
- `bookId: Long` → `bookUuid: String`（外键）
- 新增 `updated_at: Long`
- 新增 `is_deleted: Boolean = false`
- 移除 `syncVersion`、`lastSyncedAt`

**HighlightEntity：**
- `id: Long` → `uuid: String`（主键）
- `bookId: Long` → `bookUuid: String`（外键）
- 新增 `updated_at: Long`
- 新增 `is_deleted: Boolean = false`
- 移除 `syncVersion`、`lastSyncedAt`

**NoteEntity：**
- `id: Long` → `uuid: String`（主键）
- `bookId: Long` → `bookUuid: String`（外键）
- 新增 `updated_at: Long`
- 新增 `is_deleted: Boolean = false`
- 移除 `syncVersion`、`lastSyncedAt`

### 1.3 新增 SyncLogEntity（替代 ConflictRecordEntity）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 自增主键（仅本地使用） |
| `entity_type` | String | "bookmark" / "highlight" / "note" / "progress" |
| `entity_uuid` | String | 关联实体的 uuid |
| `action` | String | "local_won" / "remote_won" / "merged" / "soft_deleted" |
| `local_updated_at` | Long? | 本地时间戳 |
| `remote_updated_at` | Long? | 远端时间戳 |
| `resolved_at` | Long | 合并发生的时间 |
| `book_uuid` | String | 关联的书本 uuid |

### 1.4 数据库迁移（v2 → v3）

迁移步骤：
1. 创建新表结构（临时表）
2. 复制数据，为每条记录生成 UUID v4
3. 外键关系从 Long id 映射到新的 uuid
4. 删除旧表，重命名临时表
5. 创建新索引
6. 删除 `conflict_records` 表，创建 `sync_logs` 表

迁移时 `updated_at` 取 `System.currentTimeMillis()`，`is_deleted = false`。

---

## 二、同步核心层

### 2.1 metadata.json 新格式

```json
{
  "version": 3,
  "book": {
    "uuid": "...",
    "title": "...",
    "author": "...",
    "updated_at": 1716720000000,
    "is_deleted": false,
    "...": "其他书本字段"
  },
  "reading_progress": {
    "uuid": "...",
    "percentage": 45.0,
    "chapter_index": 3,
    "updated_at": 1716720000000,
    "is_deleted": false
  },
  "bookmarks": [
    {
      "uuid": "...",
      "chapter_name": "...",
      "chapter_index": 3,
      "position": 150,
      "updated_at": 1716720000000,
      "is_deleted": false
    }
  ],
  "highlights": [
    {
      "uuid": "...",
      "content": "...",
      "updated_at": 1716720000000,
      "is_deleted": false
    }
  ],
  "notes": [
    {
      "uuid": "...",
      "content": "...",
      "updated_at": 1716720000000,
      "is_deleted": false
    }
  ]
}
```

### 2.2 Push 流程

```
pushToRemote():
  for each local book:
    if book.updated_at > book.lastSyncedAt:
      收集该书下所有标注（含 is_deleted=true）
      序列化为 metadata.json
      上传到 Google Drive book_{uuid}/metadata.json
      book.lastSyncedAt = now()
```

关键点：
- 软删除的记录也上传，确保其他设备能收到删除信号
- `lastSyncedAt` 仅保留在 Book 级别作为同步水位线

### 2.3 Pull + Merge 流程

```
pullFromRemote():
  remote_books = list Google Drive folders

  for each remote_book:
    local_book = find by uuid

    if local_book == null:
      // 新书，直接插入（含所有标注）
      insertBook(remote_book)
      continue

    if local_book.is_deleted and remote_book.is_deleted:
      continue  // 双方都已删除，跳过

    // 合并书本本身
    mergeEntity(local_book, remote_book)

    // 合并阅读进度（特殊规则）
    mergeProgress(local_book, remote_book)

    // 合并标注（统一 LWW）
    mergeAnnotations(local_book, remote_book)
```

### 2.4 mergeEntity — 通用 LWW 合并

```
mergeEntity(local, remote):
  if remote.updated_at > local.updated_at:
    用远端数据覆盖本地
    recordSyncLog("remote_won", ...)
  else if local.updated_at > remote.updated_at:
    保留本地数据
    recordSyncLog("local_won", ...)
  // 相等则不处理
```

### 2.5 mergeProgress — 阅读进度特殊合并

```
mergeProgress(local, remote):
  if remote.is_deleted and local.is_deleted:
    return  // 双方都删除，跳过

  if remote.is_deleted and !local.is_deleted:
    // 远端已删除但本地还有进度，保留本地
    return

  if !remote.is_deleted and local.is_deleted:
    // 本地已删除但远端还有进度，用远端恢复
    apply remote to local
    return

  // 双方都有进度：取百分比更高的
  if remote.percentage >= local.percentage:
    用远端数据覆盖本地
  else:
    保留本地数据

  recordSyncLog(...)
```

### 2.6 mergeAnnotations — 标注批量合并

```
mergeAnnotations(local_book, remote_book):
  for each remote_annotation in [bookmarks, highlights, notes]:
    local = findLocalByUuid(remote_annotation.uuid)

    if local == null:
      // 本地不存在，直接插入（含 is_deleted 状态）
      insert(remote_annotation)
      continue

    // LWW 比较
    if remote_annotation.updated_at > local.updated_at:
      更新本地 = remote_annotation
      recordSyncLog("remote_won", ...)
    else if local.updated_at > remote_annotation.updated_at:
      recordSyncLog("local_won", ...)
    // 相等则不处理
```

---

## 三、数据访问层

### 3.1 DAO 默认过滤

所有查询方法默认加 `WHERE is_deleted = 0` 条件：

```kotlin
@Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid AND is_deleted = 0")
fun getBookmarksForBook(bookUuid: String): Flow<List<BookmarkEntity>>

@Query("SELECT * FROM bookmarks WHERE is_deleted = 1")
fun getDeletedBookmarks(): Flow<List<BookmarkEntity>>
```

### 3.2 删除操作改造

所有 delete 方法改为软删除：

```kotlin
@Query("UPDATE bookmarks SET is_deleted = 1, updated_at = :now WHERE uuid = :uuid")
suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())
```

移除所有物理删除 DAO 方法。

### 3.3 Repository 层

- Domain model 增加 `uuid`、`updatedAt`、`isDeleted` 字段
- Repository delete 方法调用 DAO 的 softDelete
- Repository 查询方法返回的数据自动排除 is_deleted

---

## 四、前端展示层

### 4.1 合并日志 UI（SyncLogScreen）

- 展示最近 100 条合并记录
- 每条记录显示：实体类型、操作（local_won/remote_won）、时间戳
- 从设置页进入
- 合并日志通过 Room Flow 实时更新

### 4.2 自动清理策略

- 合并日志保留最近 100 条，超出自动删除最早的
- 软删除数据 > 30 天可在空闲时物理清理（可选功能，不在首期实现）

---

## 五、影响范围

### 需要修改的文件

**数据层：**
- 所有 Entity 类（BookEntity, ReadingProgressEntity, BookmarkEntity, HighlightEntity, NoteEntity）
- 新增 SyncLogEntity
- 所有 DAO 接口
- AppDatabase（迁移 v2 → v3）
- 所有 Repository 类

**领域层：**
- 所有 Domain Model（Book, ReadingProgress, Bookmark, Highlight, Note）
- SyncService（核心重写）
- 移除 ConflictRecord 相关代码

**展示层：**
- 书架/阅读器中所有使用 id 的 ViewModel 和 Composable
- 新增 SyncLogScreen
- 设置页添加合并日志入口

**远端：**
- metadata.json 格式升级（version: 3）
- GoogleDriveClient 上传/解析逻辑适配

### 首期不包含

- 软删除数据的物理清理（30 天后清理）
- 软删除数据的回收站 UI（用户恢复已删除项）
- 多账号冲突处理
