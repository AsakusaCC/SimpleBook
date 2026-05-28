# Speed Dial FAB + 文件夹分类 + 排序功能设计

日期：2026-05-28

## 概述

为书架页面引入三项增强：Speed Dial FAB 交互重构、文件夹分类（排他归属）、用户可选排序。文件夹数据同步纳入现有 Google Drive LWW 同步体系。

## 一、Speed Dial FAB 交互

### 组件

新增 `SpeedDialFAB` 可组合函数，替换现有单 FAB。

### 行为

- **默认状态**：右下角显示 `Icons.Default.Add` 标准悬浮按钮
- **点击展开**：图标顺时针旋转 45° 变为 `Icons.Default.Close`，上方弹出 3 个 Mini FAB
- **动画**：`AnimatedVisibility` + `slideInVertically`，Mini FAB 依次弹出（从下到上）
- **关闭方式**：再次点击 FAB、点击空白区域、或执行任一操作后自动关闭
- **展开时背景**：不加遮罩，保持轻量

### Mini FAB 布局（从上到下）

| 位置 | 图标 | 标签 | 点击行为 |
|------|------|------|---------|
| 1（最上） | `Icons.Default.Add` | 导入书籍 | 触发现有文件选择器 |
| 2（中间） | `Icons.Default.CreateNewFolder` | 新建文件夹 | 弹出命名 Dialog |
| 3（最下） | `Icons.Default.Sort` | 排序 | 弹出排序选项菜单 |

## 二、数据层扩展

### Folder 实体

```kotlin
data class Folder(
    val uuid: String,          // 主键
    val name: String,          // 文件夹名称
    val createdAt: Long,       // 创建时间戳
    val updatedAt: Long,       // 更新时间戳（LWW 同步用）
    val isDeleted: Boolean,    // 软删除
    val lastSyncedAt: Long?,   // 同步时间戳
    val driveFileId: String?   // Drive 文件 ID
)
```

### Book 表变更

新增字段：`folderId: String?`

- `null` = 主书架上的书
- 非 null = 在对应文件夹内

### Room DAO 新增查询

- `getShelfBooks()` → `WHERE folderId IS NULL AND isDeleted = 0`
- `getBooksInFolder(folderId)` → `WHERE folderId = ? AND isDeleted = 0`
- `getAllFolders()` → `WHERE isDeleted = 0 ORDER BY name ASC`
- `getBookCountByFolder(folderId)` → `SELECT COUNT(*) WHERE folderId = ? AND isDeleted = 0`

### 排序

- 新增 `SortOrder` 枚举：`LAST_READ`（默认）、`NAME`
- 持久化到 `SettingsDataStore`
- ViewModel 在内存中对查询结果排序，不改变 DAO 查询

### 同步策略

- Folder 作为独立实体同步，与 Book 使用相同的 LWW + 软删除模式
- Drive 上新增 `folder_sync.json` 存储文件夹数据
- Book 的 `folderId` 变更随 Book 实体一起同步，自然参与 LWW merge

## 三、UI 渲染

### 统一数据模型

```kotlin
sealed class ShelfItem {
    data class BookItem(val book: Book, val progress: Double) : ShelfItem()
    data class FolderItem(val folder: Folder, val bookCount: Int) : ShelfItem()
}
```

主书架查询：`folderId IS NULL` 的书籍 + 所有未删除文件夹 → `List<ShelfItem>`

### 网格模式（大/小网格）

- 文件夹卡片与书籍卡片同等尺寸，3:4 比例
- 背景色：`MaterialTheme.colorScheme.secondaryContainer`
- 中央：`Icons.Outlined.Folder` 大图标（64dp）
- 底部：文件夹名称（单行，粗体）+ "共 N 本"（小字，secondary 色）
- 无进度条

### 列表模式

- 左侧：文件夹图标（40dp）
- 右侧上方：文件夹名称
- 右侧下方："共 N 本"
- 底部分割线与书籍列表项一致

### 文件夹详情页（二级视图）

- 不新增 Screen/路由，在 `BookListScreen` 内部用状态切换
- ViewModel 维护 `currentFolderId: String?` 状态，`null` = 主书架
- 进入文件夹时：顶栏标题改为文件夹名，左侧出现 `Icons.Default.ArrowBack`
- 内容区只显示该文件夹内书籍，使用同样的 `AdaptiveBookGrid`
- 顶栏布局切换下拉菜单保持可用

## 四、交互逻辑

### 长按书籍 → 移入/移出文件夹

- 长按弹出 `AlertDialog`，标题"移入文件夹"
- 对话框内容为 `LazyColumn` 显示所有文件夹，每项：文件夹名 + 书籍数
- **在文件夹内长按**：列表顶部额外显示"移回主书架"选项（`Icons.Outlined.Home`），点击将 `folderId` 置为 `null`
- **在主书架长按**：只显示文件夹列表，无"移回主书架"选项
- 若无文件夹，对话框显示"暂无文件夹，请先创建"
- 确认后调用 `moveBookToFolder(bookUuid, folderId?)` → 更新 Book 的 `folderId` 和 `updatedAt`

### FAB 排序交互

- 点击排序 Mini FAB → 在 FAB 旁弹出 `DropdownMenu`
- 两个选项：按最后阅读时间 / 按名称
- 无勾选标记，纯文字
- 选择后立即排序并关闭整个 Speed Dial

### 新建文件夹

- 点击新建文件夹 Mini FAB → 弹出 `AlertDialog` 含 `TextField`
- 输入名称后点确认 → 创建 Folder 实体并写入数据库
- 空名称不允许创建

## 五、与现有功能的兼容

- 书架顶栏"书架 🔽"布局切换逻辑保持不变
- 收藏夹（CollectionScreen）功能不受影响——收藏夹是书签/笔记，文件夹是书籍分类
- 底部导航（Books / Favorites / Settings）保持不变
- 手机和平板通过现有 `AdaptiveBookGrid` / `AdaptiveScaffold` 自适应布局自然兼容

## 六、关键文件变更清单

| 文件 | 变更 |
|------|------|
| `Book.kt` | 新增 `folderId` 字段 |
| `Folder.kt` | 新增实体 |
| `BookDao.kt` | 新增查询方法 |
| `FolderDao.kt` | 新增 DAO |
| `BookRepository.kt` | 新增文件夹相关方法 |
| `BookService.kt` | 新增文件夹业务逻辑 |
| `SettingsDataStore.kt` | 新增 SortOrder 持久化 |
| `BookListViewModel.kt` | 重构为 ShelfItem 模型，增加文件夹/排序状态 |
| `BookListScreen.kt` | 替换 FAB 为 SpeedDialFAB，支持二级视图 |
| `AdaptiveBookGrid.kt` | 支持 ShelfItem 渲染（含 FolderItem） |
| `BookCard.kt` | 新增 FolderCard 组件 |
| `ShelfItem.kt` | 新增密封类 |
| `SortOrder.kt` | 新增枚举 |
| 同步相关文件 | Folder 实体同步、Book folderId 字段同步 |
| `AppDatabase.kt` | 新增 Folder 表，Book 表 migration |
