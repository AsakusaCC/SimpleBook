# SimpleBook 电子书阅读器 — 设计规格

## Context

用户需要开发一款面向国际市场的 Android 电子书阅读 app，支持 EPUB/TXT 格式，适配手机和 Pad，分期发布（v1 本地阅读，v2 Google Drive 云同步）。

## 版本规划

- **v1.0** — 本地阅读：导入、阅读、书签、高亮、笔记、目录导航、手机+Pad 适配
- **v2.0** — 云同步：Google Drive API 集成、书籍同步、阅读记录跨设备同步

## 技术选型

| 组件 | 选择 | 用途 |
|------|------|------|
| 语言 | Kotlin | 主开发语言 |
| UI | Jetpack Compose | 声明式 UI，天然支持多屏幕适配 |
| DI | Hilt | 依赖注入 |
| 本地存储 | Room | 书籍元数据、阅读进度、书签、笔记 |
| EPUB 解析 | epublib (ktop) | EPUB 文件解压与解析 |
| TXT 解析 | 自实现 | 编码检测 + 章节分割 |
| EPUB 渲染 | WebView | XHTML+CSS 原生渲染 |
| TXT 渲染 | Compose Text | 纯文本高性能渲染 |
| 图片加载 | Coil | 封面、内嵌图片 |
| 导航 | Compose Navigation | 页面路由 |
| 多屏适配 | WindowSizeClass API | Compact/Medium/Expanded 布局切换 |

## 分层架构

```
┌─────────────────────────────────┐
│         UI Layer (Compose)       │
│  BookList / Reader / Settings   │
├─────────────────────────────────┤
│       ViewModel Layer            │
│  状态管理、业务逻辑协调           │
├─────────────────────────────────┤
│       Domain Layer               │
│  BookService / ReadingService    │
│  BookmarkService / NoteService   │
├─────────────────────────────────┤
│       Data Layer                 │
│  Room DB / File System          │
└─────────────────────────────────┘
```

## 数据模型

**Book** — 书籍元数据
- id (Long, PK), title, author, filePath, format (EPUB/TXT), coverPath, fileSize, addedAt, lastReadAt

**ReadingProgress** — 阅读进度
- id (Long, PK), bookId (FK→Book), chapterIndex, charOffset, percentage (0.0~1.0), updatedAt

**Bookmark** — 书签
- id (Long, PK), bookId (FK→Book), chapterIndex, charOffset, name, createdAt

**Highlight** — 高亮标注
- id (Long, PK), bookId (FK→Book), chapterIndex, startOffset, endOffset, color, note, createdAt

**Note** — 笔记
- id (Long, PK), bookId (FK→Book), highlightId (FK→Highlight, nullable), chapterIndex, charOffset, content, createdAt

v2 同步时为每个实体增加 `syncVersion` + `lastSyncedAt` 字段支持增量同步。

## UI 结构

### 页面导航

```
App
├── BookListScreen — 书架（Pad 网格/手机列表，FAB 导入）
├── ReaderScreen — 阅读界面
│   ├── 内容渲染区（EPUB WebView / TXT Compose）
│   ├── 顶部栏（点击唤出）：书名、返回
│   ├── 底部栏（点击唤出）：进度条、章节跳转
│   └── 侧边栏（滑动唤出）：目录、书签、笔记
├── BookmarkScreen — 书签管理
├── NoteScreen — 笔记管理
└── SettingsScreen — 阅读/通用设置
```

### 手机 vs Pad 适配

| 场景 | 手机 (Compact) | Pad (Expanded) |
|------|----------------|----------------|
| 书架 | 列表视图，每行 1-2 本 | 网格视图，每行 3-5 本 |
| 阅读器 | 全屏沉浸阅读 | 可选双栏模式（横屏） |
| 目录/书签/笔记 | 底部 Sheet / 覆盖层 | 侧边面板，与阅读内容并排 |
| 导航 | 标准返回栈 | Navigation Rail 或侧边栏 |

### 阅读器交互

- 左右滑动/点击边缘翻页（EPUB 按章节，TXT 按分页）
- 长按文字 → 操作菜单（高亮、复制、添加笔记）
- 点击中央 → 唤出/隐藏工具栏

## EPUB 解析与渲染

**解析：** epublib 解压 → 提取元数据/TOC/章节 XHTML/内嵌资源
**渲染：** 每章独立 WebView，JS Bridge 注入用户设置，ViewPager 翻页，预加载前后各一章
**高亮：** WebView getSelection + JS Bridge 回调到原生层

## TXT 解析与渲染

**解析：** 编码检测 (UTF-8/GBK/GB18030) → 按空行或正则（第X章/Chapter X）分章 → 无法识别时按固定字符数分页
**渲染：** Compose LazyColumn + 动态分页计算，缓存分页结果

## 书籍导入流程

系统文件选择器 (SAF) → 过滤 .epub/.txt → 多选 → 复制到应用沙箱 → 后台解析元数据+封面 → 写入 Room → 书架刷新

## v1.0 范围边界

**包含：** 导入、书架、阅读器、目录、书签、高亮、笔记、阅读设置、手机+Pad 适配
**排除（v2+）：** Google Drive 云同步、主题编辑、TTS、自定义字体、阅读统计、OPDS 书库、PDF

## 项目包结构

```
com.ebookreader.simplebook/
├── data/
│   ├── local/       (Room DAO、Entity、Database)
│   ├── repository/  (BookRepository、ReadingRepository)
│   └── parser/      (EpubParser、TxtParser)
├── domain/
│   ├── model/       (业务模型)
│   └── service/     (BookService、ReadingService)
├── ui/
│   ├── booklist/    (BookListScreen + ViewModel)
│   ├── reader/      (ReaderScreen + ViewModel)
│   ├── bookmark/    (BookmarkScreen + ViewModel)
│   ├── note/        (NoteScreen + ViewModel)
│   ├── settings/    (SettingsScreen + ViewModel)
│   └── components/  (共享 UI 组件、AdaptiveLayout)
├── di/              (Hilt Module)
└── SimpleBookApp.kt (Application + NavHost)
```

## 实施步骤

### Phase 1: 项目骨架
1. Android Studio 项目初始化（Kotlin + Compose + Hilt + Room）
2. 包结构搭建
3. Room 数据库定义（Entity + DAO + Database）
4. Hilt Module 配置
5. Navigation NavHost 搭建

### Phase 2: 书籍导入与管理
6. 文件导入（SAF 文件选择器）
7. EPUB 解析器（epublib 集成）
8. TXT 解析器（编码检测 + 章节分割）
9. BookRepository 实现
10. BookListScreen（网格/列表 + 自适应布局）

### Phase 3: 阅读器核心
11. EPUB 阅读器（WebView 渲染 + 翻页 + JS Bridge）
12. TXT 阅读器（Compose 渲染 + 分页）
13. ReaderScreen UI（工具栏唤出、进度条）
14. ReadingProgress 持久化（自动保存/恢复）

### Phase 4: 阅读增强功能
15. 目录导航（TOC 侧边栏）
16. 书签功能（添加/删除/跳转）
17. 高亮标注（文本选中 + 颜色选择）
18. 笔记功能（批注 + 独立笔记）

### Phase 5: 设置与完善
19. SettingsScreen（字体大小、行间距、背景色）
20. 手机 + Pad 布局适配验证
21. 端到端测试与 Bug 修复

## 验证方式

1. **构建验证：** `./gradlew assembleDebug` 成功
2. **单元测试：** `./gradlew test` — 覆盖解析器、Repository、Service
3. **手动验证：**
   - 在手机模拟器上导入 EPUB/TXT，阅读、添加书签/高亮/笔记
   - 在 Pad 模拟器上验证自适应布局
   - 杀进程重启，验证阅读进度恢复
   - 验证不同编码的 TXT 文件正常显示
