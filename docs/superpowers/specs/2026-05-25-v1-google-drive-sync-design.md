# SimpleBook v1.0 — Google Drive 同步 + 封面进度显示

## 概述

v1.0 在 v0.5 本地阅读基础上新增 Google Drive 云同步和书架封面进度显示。同步覆盖手机与平板之间，支持阅读进度、书签/高亮/笔记、书籍文件的同步，带版本冲突检测与手动解决。

**核心约束：同步是可选功能。** 不登录 Google Drive 时，所有本地阅读功能（导入、阅读、书签、高亮、笔记、设置、封面进度显示）完全正常使用，无任何功能降级。App 不应因未登录而弹出阻塞性提示或限制任何操作。

## 同步需求

- **同步内容：** 阅读进度、书签、高亮、笔记、书籍文件（EPUB/TXT）
- **不同步：** 阅读设置（字号、行距、背景色、语言）
- **目标设备：** 同一 Google 账号下的 Android 手机 ↔ 平板
- **触发方式：** 自动（启动拉取 + 阅读中定期上传 + 切后台保存）+ 手动同步按钮
- **冲突策略：** 版本历史追踪，冲突时逐条展示，用户手动选择保留本地或远端

## 架构方案：按书籍粒度同步

使用 Google Drive App Folder（用户不可见），每本书一个子文件夹。

```
Drive AppFolder/
├── sync-manifest.json          # 全局清单：bookId → Drive 文件夹 ID 映射
└── books/
    ├── {uuid}/
    │   ├── book.epub           # 书籍文件
    │   └── metadata.json       # 含 syncVersion 的同步数据
    └── {uuid}/
        ├── book.txt
        └── metadata.json
```

### 为什么按书籍粒度

- 增量同步：只处理有变化的书籍，不传全量数据
- 冲突隔离：一本书的冲突不影响其他书
- 按书管理：删除/重新同步单本书

## 认证与 Drive 集成

- **认证：** Google Sign-In via Credential Manager API
- **Scope：** `https://www.googleapis.com/auth/drive.appfolder`
- **首次使用：** 设置页/书架页显示「登录 Google Drive」按钮，引导用户授权
- **Token 管理：** 自动刷新，过期时提示重新登录

## 同步引擎

`SyncService` 负责全部同步逻辑：

**上传流程：**
1. 扫描本地所有 `lastSyncedAt == null || updatedAt > lastSyncedAt` 的书籍
2. 序列化该书所有同步数据为 metadata.json
3. 上传书籍文件（首次）或检查文件未变则跳过
4. 更新本地 `lastSyncedAt` 和 `syncVersion`

**下载流程：**
1. 拉取 sync-manifest.json，比对本地书籍列表
2. 对每本书拉取 metadata.json，比对 `syncVersion`
3. 仅远端更新 → 合并到本地（更新 + 设置 lastSyncedAt）
4. 双方都有修改 → 标记冲突，写入 ConflictRecord
5. 本地新书（无远端对应）→ 提示用户是否上传

**自动触发时机：**
- App 启动时：拉取远端最新数据
- 阅读中：每 5 分钟自动上传当前进度
- App 切后台/暂停：立即保存当前状态并上传
- 网络恢复时：自动重试失败的同步操作

**手动触发：**
- 书架页顶部工具栏：同步状态图标 + 「立即同步」按钮
- 设置页：同步管理区域（登录/登出、立即同步、同步历史）

## 数据模型变更

### 现有 Entity 扩展

所有同步实体新增字段：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `syncVersion` | Long | 1 | 每次修改自增 |
| `lastSyncedAt` | Long? | null | 最后成功同步时间戳 |

适用实体：`BookEntity`、`ReadingProgressEntity`、`BookmarkEntity`、`HighlightEntity`、`NoteEntity`

### BookEntity 额外字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `driveFileId` | String? | null | Drive 中该书文件夹的 ID |

### 新增 Entity：ConflictRecord

```
ConflictRecord:
  id: Long (PK)
  bookId: Long (FK → Book)
  entityType: String       # "progress" / "bookmark" / "highlight" / "note"
  entityId: Long           # 本地实体 ID
  localSyncVersion: Long
  remoteSyncVersion: Long
  localData: String        # JSON 序列化的本地数据
  remoteData: String       # JSON 序列化的远端数据
  createdAt: Long
  resolvedAt: Long?        # null = 未解决
```

### metadata.json 结构

```json
{
  "bookId": 123,
  "title": "...",
  "author": "...",
  "syncVersion": 5,
  "updatedAt": 1716633600000,
  "progress": {
    "chapterIndex": 3,
    "charOffset": 1500,
    "percentage": 0.42,
    "syncVersion": 3,
    "updatedAt": 1716633600000
  },
  "bookmarks": [
    {
      "chapterIndex": 1,
      "charOffset": 200,
      "name": "重要段落",
      "syncVersion": 2,
      "createdAt": 1716630000000
    }
  ],
  "highlights": [...],
  "notes": [...]
}
```

### 数据库迁移

Room Migration 脚本：为现有表添加新字段，默认值确保不影响已有数据。不删除任何现有数据。

## 冲突解决 UI

### 冲突检测

拉取远端 metadata 后逐条比对：
- 远端 `syncVersion > 本地 syncVersion` **且** 本地 `lastSyncedAt < updatedAt` → 冲突
- 仅远端更新 → 直接合并
- 仅本地更新 → 正常上传

### 冲突展示

- 检测到冲突时，顶部 SnackBar：「有 N 条数据冲突，点击解决」
- 点击进入全屏冲突解决页面，逐条展示：
  - 类型标签（进度/书签/高亮/笔记）
  - 左侧：本地内容预览
  - 右侧：远端内容预览
  - 两个按钮：「保留本地」/「使用远端」
  - 可滑动跳过（稍后处理）
- 底部快捷按钮：「全部保留本地」/「全部使用远端」

### 解决后处理

- 选中版本写入本地数据库
- `syncVersion` = max(local, remote) + 1
- `lastSyncedAt` = 当前时间
- 立即上传到 Drive

## 封面阅读进度显示

### 实现

在 BookCard 组件封面图片底部叠加进度指示：

- 水平进度条：高度 4dp，背景半透明黑色（0.4 alpha），前景白色
- 进度条右端叠加百分比文字（如 `42%`），白色小字
- 无阅读进度时（`percentage == 0f || percentage == null`）不显示任何进度指示

### 数据流

1. BookListViewModel 加载书架时，通过 ReadingService 查询每本书的 ReadingProgress
2. 将 percentage 传入 BookCard
3. BookCard 内部用 `Box` 叠加 `AsyncImage` + 底部进度条

## 新增文件结构

```
data/remote/                     # 新增
  GoogleDriveClient.kt           # Drive API 封装
  SyncMetadata.kt                # metadata.json 序列化模型
  SyncRepository.kt              # 同步数据仓库
domain/model/
  ConflictRecord.kt              # 冲突记录模型（新增）
domain/service/
  SyncService.kt                 # 同步引擎核心（新增）
ui/sync/                         # 新增
  SyncScreen.kt                  # 冲突解决 UI
  SyncViewModel.kt
ui/components/
  BookCard.kt                    # 修改：叠加进度条
data/local/
  SyncDao.kt                     # 冲突记录 DAO（新增）
di/
  SyncModule.kt                  # Hilt DI（新增）
SimpleBookApp.kt                 # 修改：添加 SyncScreen 路由
```

## 新增依赖

```gradle
implementation 'com.google.android.gms:play-services-auth:21.x.x'
implementation 'com.google.apis:google-api-services-drive:v3-rev2024xxxx-2.0.0'
implementation 'com.google.http-client:google-http-client-gson:1.x.x'
```

## 不在 v1.0 范围内

- 阅读设置同步
- 自动后台定期同步（仅 App 运行时同步）
- 多账户支持
- 其他云存储服务（Dropbox、OneDrive 等）
- 阅读统计、TTS、自定义字体
