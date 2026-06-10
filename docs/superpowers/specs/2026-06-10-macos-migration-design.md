# SimpleBook macOS Desktop 迁移设计文档

> 日期: 2026-06-10
> 状态: 已批准
> 分支策略: 独立分支，不动 main

## 1. 背景与目标

将现有 Android 电子书阅读器 SimpleBook (v0.8.6) 通过 Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP) 改造为同时支持 macOS 和 Windows 的桌面应用。

### 1.1 核心决策

| 维度 | 决策 |
|------|------|
| 技术路线 | Kotlin Multiplatform + Compose Multiplatform |
| 代码共享 | 单仓库 KMP 共享（方案 A：模块拆分式重构） |
| 目标平台 | macOS 优先（Apple M4），架构预留 Windows |
| Google Drive | 完整双向同步，桌面端 OAuth 2.0 浏览器授权 |
| PC 交互增强 | 拖放操作 + 系统托盘集成 |
| 数据存储 | Room KMP（升级现有 Room 2.7.0 为跨平台版） |
| DI 框架 | Hilt → Koin（Hilt 不支持 Desktop） |
| 分支策略 | 独立分支开发，不影响 main |

### 1.2 约束

- 不动 main 分支现有代码，所有工作在独立分支进行
- 迁移后 Android 端功能和行为不受影响
- 长期目标同时支持 macOS 和 Windows

## 2. 项目结构

```
Ebookreader/
├── gradle/
│   └── libs.versions.toml          # 版本目录（升级依赖）
├── build.gradle.kts                # 根构建文件
├── settings.gradle.kts             # include :shared, :androidApp, :desktopApp
│
├── shared/                         # 核心共享模块
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/      # 跨平台代码
│       │   └── com.simplebook.shared/
│       │       ├── data/
│       │       │   ├── local/      # Room Entity, DAO, Database (KMP)
│       │       │   ├── repository/ # Repository 实现
│       │       │   └── sync/       # Google Drive REST API 同步逻辑
│       │       ├── domain/         # UseCase, Model
│       │       ├── ui/             # Compose UI 组件（跨平台）
│       │       │   ├── screen/
│       │       │   ├── component/
│       │       │   ├── theme/
│       │       │   └── navigation/
│       │       └── di/             # Koin DI 模块定义
│       ├── androidMain/kotlin/     # Android 专属
│       │   └── com.simplebook.shared/
│       │       └── platform/       # Room Driver, Google Sign-In
│       └── desktopMain/kotlin/     # Desktop 专属
│           └── com.simplebook.shared/
│               └── platform/       # Room Driver, OAuth 2.0
│
├── androidApp/                     # Android 应用入口
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/                 # Application, MainActivity
│
└── desktopApp/                     # Desktop 应用入口
    ├── build.gradle.kts
    └── src/jvmMain/kotlin/         # main(), 窗口配置, 托盘, 拖放
```

### 2.1 expect/actual 边界

只有以下接口需要平台差异处理：

```kotlin
// commonMain
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

expect class AuthProvider {
    fun signIn(scope: String): Result<String>
    fun signOut()
    fun getCurrentUser(): UserInfo?
}

expect fun openUrl(url: String)
expect fun getStoragePath(): String          // 书籍文件存储路径
expect fun getDatabasePath(): String         // 数据库文件路径
expect fun pickFiles(filter: String): List<String>  // 文件选择器
```

## 3. 数据层迁移

### 3.1 Room KMP

当前 7 个实体，全部使用 UUID 主键 + 软删除 + LWW 同步字段：

- BookEntity, ReadingProgressEntity, BookmarkEntity
- HighlightEntity, NoteEntity, FolderEntity, SyncLogEntity

**迁移策略：Entity 和 DAO 几乎零改动，只搬位置到 commonMain。**

### 3.2 平台驱动

**Android (androidMain)**：
```kotlin
actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSQLiteDriver(SimpleBookDatabase.Schema)
    }
}
```

**Desktop (desktopMain)**：
```kotlin
actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbPath = getDatabasePath()
        return JdbcSqliteDriver("jdbc:sqlite:$dbPath").also {
            SimpleBookDatabase.Schema.create(it)
        }
    }
}
```

### 3.3 数据库迁移策略

| 场景 | 处理方式 |
|------|---------|
| Schema 和 Migration 定义 | 放在 commonMain，两端共享 |
| 桌面端首次安装 | 直接 `Schema.create()` 创建最新 Schema |
| 后续版本升级 | 新增 Migration 在 commonMain 定义 |

### 3.4 文件存储路径

| 平台 | 书籍文件 | 数据库 |
|------|---------|--------|
| Android | `context.filesDir/books/` | Room 默认路径 |
| macOS | `~/Library/SimpleBook/books/` | `~/Library/SimpleBook/database/` |
| Windows | `%APPDATA%/SimpleBook/books/` | `%APPDATA%/SimpleBook/database/` |

### 3.5 影响评估

| 组件 | 改动量 | 说明 |
|------|--------|------|
| Entity | 零改动 | 注解和字段全部兼容 KMP |
| DAO | 零改动 | Room KMP 的 DAO 完全支持 |
| Database 类 | 小改 | 构造函数注入 SqlDriver，去掉 Android Context |
| Repository | 零改动 | 纯 Kotlin，无平台依赖 |
| Migration | 零改动 | 放 commonMain，两端共享 |

## 4. Google Drive 同步

### 4.1 架构

```
commonMain (共享)
├── AuthProvider (expect)           → access token
├── DriveSyncService               → 完全复用现有同步逻辑
├── Google Drive REST API v3       → 上传/下载/冲突解决/LWW
│
├── androidMain                    ├── desktopMain
│   └── Google Sign-In             │   └── OAuth 2.0 PKCE
│       (play-services-auth)       │       (浏览器授权流程)
```

### 4.2 桌面端 OAuth 2.0 流程

1. 应用生成 PKCE code_verifier + code_challenge
2. 打开系统浏览器 → Google OAuth 授权页面
3. 用户登录并授权
4. Google 重定向到 localhost HTTP Server
5. 应用捕获 authorization code
6. 用 code + code_verifier 换取 access token + refresh token
7. 后续请求使用 access token，过期自动刷新

### 4.3 依赖

```
共享 (commonMain):
  com.google.api-client:google-api-client
  com.google.apis:google-api-services-drive
  com.google.http-client:google-http-client-gson

仅 Android:
  com.google.android.gms:play-services-auth

仅 Desktop: 无额外依赖
```

## 5. DI 迁移：Hilt → Koin

### 5.1 映射

| Hilt | Koin |
|------|------|
| `@Module` + `@InstallIn` | `module { }` |
| `@Provides` | `single { }` / `factory { }` |
| `@Inject` 构造函数 | 构造函数无需注解 |
| `@HiltViewModel` | `viewModelOf()` |
| `@Singleton` | `single { }` |

### 5.2 模块组织

**共享 (commonMain)**：
```kotlin
val appModule = module {
    single<BookRepository> { BookRepositoryImpl(get()) }
    single<SyncRepository> { SyncRepositoryImpl(get()) }
}

val dataModule = module {
    single { getDatabaseDriverFactory().createDriver() }
    single { SimpleBookDatabase(get()) }
}
```

**平台专属**：
```kotlin
// androidMain
val androidModule = module {
    single<AuthProvider> { AndroidAuthProvider(androidContext()) }
}

// desktopMain
val desktopModule = module {
    single<AuthProvider> { DesktopOAuthProvider() }
}
```

## 6. UI 层

### 6.1 共享策略

现有 Compose UI 组件直接搬入 shared/commonMain，无需重写。

### 6.2 需要适配的组件

| 组件 | 改动量 | 说明 |
|------|--------|------|
| AdaptiveLayout | 小改 | 可加更宽的桌面布局断点 |
| 返回导航 | 小改 | Desktop 用 Esc 键返回 |
| 文件选择 | 新增 | expect/actual 抽象文件对话框 |
| Toast/Snackbar | 无改 | Compose Snackbar 已跨平台 |
| 权限请求 | 简化 | Desktop 不需要 Android 权限系统 |

### 6.3 ViewModel 改造

去掉 `@HiltViewModel` 注解，改用 Koin `viewModelOf()` 声明。ViewModel 内部逻辑不变。

## 7. 桌面端特性

### 7.1 窗口配置

- 默认尺寸: 1200x800 dp
- 最小尺寸: 800x600 dp
- 居中显示
- 可配置关闭行为（最小化到托盘 or 退出）

### 7.2 拖放操作

通过 Compose Desktop `Modifier.onExternalDrag` 实现：

- 拖入 .epub/.txt 文件 → 导入书籍
- 拖入文件夹 → 批量导入
- 流程: 检测文件类型 → 验证格式 → 拷贝到本地存储 → 写入数据库 → 刷新书架

### 7.3 系统托盘

- 点击关闭按钮 → 最小化到托盘（可配置为退出）
- 点击托盘图标 → 恢复窗口
- 托盘右键菜单: 打开 / 继续阅读 / 退出
- 开机自启（可选）

## 8. 依赖升级

| 依赖 | 当前 | 目标 | 说明 |
|------|------|------|------|
| Room | 2.7.0 | 2.7.0+ (KMP 模式) | 版本不变，改配置 |
| Compose | BOM 2025.05.00 | CMP 1.9.0+ | Jetpack Compose → CMP |
| Hilt | 有 | → 移除 | 替换为 Koin |
| Koin | 无 | 4.x | 新增 KMP DI |
| play-services-auth | 21.3.0 | Android 保留 | Desktop 不引入 |
| Navigation | AndroidX 版 | CMP 版 | 切换多平台版本 |

## 9. 难度评估

### 9.1 各模块难度

```
数据层迁移                    ■■   (低中)
Room 平台驱动                 ■■   (低中)
Repository 层迁移             ■    (低)
Google Drive 同步逻辑迁移     ■■   (低中)
DI 迁移 (Hilt → Koin)        ■■■  (中)
OAuth 2.0 Desktop 认证        ■■■  (中)
UI 层迁移                     ■■   (低中)
ViewModel 改造               ■■■  (中)
项目结构重构 (模块拆分)        ■■■■ (中高)
桌面端特性 (托盘/拖放)         ■■■  (中)
打包分发配置                  ■■   (低中)
Android 端回归验证            ■■■  (中)
```

**总体难度: 中高** — 主要工作量在模块拆分和 DI 迁移，而非写新代码。

### 9.2 风险

| 风险 | 概率 | 影响 | 应对 |
|------|------|------|------|
| Room KMP 配置踩坑 | 中 | 中 | 提前做 Spike 验证 |
| Compose API 差异 | 低 | 低 | Material 3 组件已对齐 |
| Google OAuth 审核 | 中 | 中 | 开发用测试模式，发布时验证 |
| Android 回归 bug | 中 | 中 | 每模块完成后跑测试 |
| 模块拆分编译问题 | 中 | 低 | 增量拆分，每步确保编译通过 |

## 10. 实施阶段

```
Phase 1: 骨架搭建
  → 创建分支 + KMP 多模块结构 + Gradle 配置
  → 确保 Android 端编译通过

Phase 2: 数据层
  → Entity/DAO/Database 迁移到 shared/commonMain
  → Room 驱动 expect/actual
  → Android 端数据功能验证

Phase 3: DI 迁移
  → Hilt → Koin
  → Android 端功能验证

Phase 4: UI 迁移
  → Screen/Component/Theme 搬入 shared/commonMain
  → ViewModel 改造
  → Android 端 UI 验证

Phase 5: Desktop 入口
  → desktopApp 模块 + 窗口配置
  → 桌面端首次启动验证

Phase 6: Google Drive 桌面端
  → OAuth 2.0 认证实现
  → 同步逻辑验证

Phase 7: 桌面端特性 + 打包
  → 拖放 + 系统托盘
  → DMG 打包
```

### 10.1 Android 端兼容性保证

迁移完成后，继续开发 Android 端不受影响：
- 共享代码放在 commonMain，Android 照常使用
- Android 专属代码放在 androidMain，功能不受限
- 开发体验与之前相同，只是文件位置变了
- main 分支在合并分支前不受影响
