# 收藏页：书签与笔记聚合展示

日期：2026-05-26

## 概述

实现首页"收藏"按钮功能，展示所有书籍的书签和笔记列表。用户可按书分组浏览、点击跳转至阅读器对应位置、滑动删除。支持手机端和 Pad 端自适应布局，数据通过现有 Google Drive 同步机制跨设备同步。

## 需求

1. 点击首页"收藏"按钮，进入收藏页
2. 收藏页包含两个列表：书签和笔记，按书籍分组展示
3. 点击书签/笔记，直接跳转至对应书籍的对应阅读位置
4. 支持左滑删除
5. 空状态显示简洁文字提示
6. 适配手机端（Tab 切换）和 Pad 端（左右并排）
7. 删除操作通过 Repository 执行，确保 syncVersion 递增以触发 Google Drive 同步

## 导航与路由

### 新增路由

- `Screen.Collection("collection")` — 收藏页

### 改动

- `AdaptiveScaffold`：收藏按钮路由从 `Screen.BookList.route` 改为 `Screen.Collection.route`
- `SimpleBookNavHost`：新增 `collection` composable，挂载 `CollectionScreen`
- `Screen.Reader`：扩展 query 参数 `charOffset` 和 `chapterIndex`，支持从外部跳转到指定位置

### 导航流程

```
首页 → 点"收藏" → CollectionScreen
  → 点某条书签/笔记 → navigate("reader/{bookId}?charOffset={x}&chapterIndex={y}")
```

## 数据层

### 聚合方式

不改动 Room 实体，在 ViewModel 层组合数据：

1. `BookmarkRepository.getAllBookmarks()` — 已有，返回全部书签
2. `NoteRepository.getAllNotes()` — 已有，返回全部笔记
3. `BookRepository` 获取全部书籍，建立 `bookId → Book` 映射
4. ViewModel 按 bookId 分组，形成 `Map<Book, List<Bookmark>>` 和 `Map<Book, List<Note>>`

### 条目展示信息

| 条目类型 | 显示内容 |
|---------|---------|
| 书签 | `name`（用户命名），为空时显示"第 N 章" |
| 笔记 | `content` 前 80 字预览 |
| 通用 | 创建时间（相对时间，如"3 天前"） |

### 删除

- 通过 Repository 操作（非直接 DAO），确保 `syncVersion` 递增
- SyncService 在下次同步时自动检测变更

## UI 布局

### 手机端（Compact）

```
+------------------------+
|  收藏      [书签|笔记]   |  ← TopAppBar + TabRow
+------------------------+
|  📖 三体         (3) ▾  |  ← 书名 + 数量，可折叠
|  ├ Ch.1: xxx...  3天前  |
|  ├ Ch.5: xxx...  1周前  |  ← 每条可点击跳转，可左滑删除
|  └ Ch.12: xxx... 2周前  |
|                        |
|  📖 1984         (1) ▾  |
|  └ Ch.3: xxx...  昨天   |
+------------------------+
|  📚  ❤️  ⚙️              |  ← BottomNavigationBar
+------------------------+
```

### Pad 端（Expanded）

```
+----+-----------------------------+-----------------------------+
| 📚 |          收 藏                                           |
+----+-----------------------------+-----------------------------+
| ❤️ |   书  签                   |   笔  记                     |
+----+-----------------------------+-----------------------------+
| ⚙️ | 📖 三体 (3)                | 📖 三体 (2)                  |
|    | ├ Ch.1: ...      3天前     | ├ Ch.3: ...      昨天        |
|    | ├ Ch.5: ...      1周前     | └ Ch.7: ...      3天前       |
|    | └ Ch.12: ...     2周前     |                              |
|    |                           | 📖 1984 (1)                  |
|    | 📖 1984 (1)                | └ Ch.3: ...      上周        |
|    | └ Ch.3: ...      昨天     |                              |
+----+-----------------------------+-----------------------------+
```

### 关键差异

| | 手机端 | Pad 端 |
|---|---|---|
| 布局 | TabRow 切换，单列 | 左右双列并排，无 Tab |
| 导航 | BottomNavigationBar | NavigationRail |
| 书签/笔记切换 | 点击 Tab | 同屏展示 |

### 交互细节

- **分组**：默认展开，点击书名行可折叠/展开
- **滑动删除**：`SwipeToDismiss`，向左滑出现红色删除区域
- **点击跳转**：`navController.navigate(Reader.createRoute(bookId, charOffset, chapterIndex))`
- **空状态**：简洁文字提示（"还没有书签" / "还没有笔记"）

## 点击跳转与阅读器联动

### Reader 路由扩展

```
"reader/{bookId}?charOffset={charOffset}&chapterIndex={chapterIndex}"
```

- `charOffset` 和 `chapterIndex` 为可选 query 参数，默认值 0
- 现有不带参数的调用不受影响

### ReaderScreen 改动

- 从 `SavedStateHandle` 读取 `charOffset` 和 `chapterIndex`
- 如果值 > 0，初始化时定位到对应位置

## Google Drive 同步

无需额外改动。收藏页是对本地数据的聚合展示，数据增删走现有 Repository → SyncService 链路，同步机制自动处理。

## 新增文件

| 文件 | 职责 |
|------|------|
| `ui/collection/CollectionScreen.kt` | 收藏页 UI（含手机端/Pad 端自适应） |
| `ui/collection/CollectionViewModel.kt` | 聚合书签、笔记、书籍数据 |

## 改动文件

| 文件 | 改动 |
|------|------|
| `ui/navigation/Screen.kt` | 新增 `Collection` 路由 |
| `ui/navigation/SimpleBookNavHost.kt` | 注册 `collection` composable |
| `ui/components/AdaptiveScaffold.kt` | 收藏按钮路由指向 `Collection` |
| `ui/reader/ReaderScreen.kt` | 接收并处理 `charOffset`、`chapterIndex` 参数 |
| `domain/service/BookmarkService.kt` | 确认 `deleteBookmark` 递增 syncVersion |
| `domain/service/NoteService.kt` | 确认 `deleteNote` 递增 syncVersion |
