# SimpleBook macOS Desktop 迁移实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将现有 Android SimpleBook 电子书阅读器通过 KMP+CMP 改造为同时支持 macOS（和未来 Windows）的桌面应用。

**架构：** 单仓库三模块结构 — `:shared`（跨平台核心，含数据层/Domain层/UI层）、`:androidApp`（Android 入口）、`:desktopApp`（Desktop JVM 入口）。通过 expect/actual 处理平台差异（数据库驱动、认证、文件路径、文件选择器）。DI 从 Hilt 迁移到 Koin。

**技术栈：** Kotlin Multiplatform, Compose Multiplatform 1.9.0+, Room KMP 2.7.0+, Koin 4.x, Google Drive REST API v3

**参考规格：** `docs/superpowers/specs/2026-06-10-macos-migration-design.md`

---

## 文件结构总览

### 将创建的文件

```
shared/
  build.gradle.kts
  src/commonMain/kotlin/com/ebookreader/simplebook/
    data/local/entity/          ← 7 Entity 文件从 :app 移入
    data/local/dao/             ← 7 DAO 文件从 :app 移入
    data/local/SimpleBookDatabase.kt
    data/local/PlatformPath.kt         (expect: getBooksDir, getDatabaseDir)
    data/repository/            ← 8 Repository 文件从 :app 移入
    data/remote/SyncMetadata.kt
    data/remote/GoogleDriveClient.kt   (重构：expect credential)
    data/remote/DriveCredential.kt     (expect class)
    data/parser/               ← 6 Parser 文件从 :app 移入
    domain/model/              ← 17 Model 文件从 :app 移入
    domain/service/            ← 8 Service 文件从 :app 移入（除 SyncForegroundService）
    domain/service/SyncStatus.kt
    ui/theme/                  ← 2 Theme 文件从 :app 移入
    ui/navigation/             ← 2 Navigation 文件从 :app 移入
    ui/screen/                 ← 7 Screen+ViewModel 对从 :app 移入
    ui/components/             ← 6 Component 文件从 :app 移入
    di/AppModule.kt
    di/PlatformModule.kt              (expect val)
  src/androidMain/kotlin/com/ebookreader/simplebook/
    platform/DatabaseDriverFactory.kt (actual)
    platform/PlatformPath.kt          (actual)
    platform/DriveCredential.kt       (actual)
    platform/AuthProvider.kt          (actual, 包装现有 AuthManager)
    data/local/SettingsDataStore.kt   (Android DataStore)
    data/remote/AuthManager.kt        (保留 Android Google Sign-In)
    data/remote/GoogleDriveClient.kt  (Android actual, 用 GoogleAccountCredential)
    domain/service/SyncForegroundService.kt
  src/desktopMain/kotlin/com/ebookreader/simplebook/
    platform/DatabaseDriverFactory.kt (actual, JdbcSqliteDriver)
    platform/PlatformPath.kt          (actual, ~/Library/SimpleBook)
    platform/DriveCredential.kt       (actual, OAuth 2.0 token)
    platform/AuthProvider.kt          (actual, OAuth 2.0 PKCE)
    platform/DesktopOAuthServer.kt    (localhost 回调服务器)
    platform/TokenStorage.kt          (本地 token 存储)
    data/local/SettingsDataStore.kt   (Properties 文件)
    data/remote/GoogleDriveClient.kt  (Desktop actual, 用 GoogleCredential)
androidApp/
  build.gradle.kts
  src/main/
    AndroidManifest.xml
    kotlin/com/ebookreader/simplebook/
      SimpleBookApp.kt
      MainActivity.kt
desktopApp/
  build.gradle.kts
  src/jvmMain/kotlin/com/ebookreader/simplebook/
    Main.kt
    TrayManager.kt
    DragDropHandler.kt
```

### 将删除/替换的文件

- `app/` 模块整体移除（代码已迁移到 shared + androidApp）
- 原 Hilt DI 模块 (`di/DatabaseModule.kt`, `di/RepositoryModule.kt`, `di/SyncModule.kt`) → 替换为 Koin 模块

---

## Phase 1：骨架搭建

### 任务 1.1：创建迁移分支

- [ ] **步骤 1：从 main 创建分支**

```bash
git checkout -b feat/kmp-desktop-migration main
```

- [ ] **步骤 2：确认当前状态干净**

```bash
git status
```

预期：只有未跟踪的 Logo 文件，无未提交的代码变更。

---

### 任务 1.2：更新根 build.gradle.kts

**文件：**
- 修改：`build.gradle.kts`（根目录）

- [ ] **步骤 1：添加 KMP 和 CMP 插件声明**

```kotlin
plugins {
    id("com.android.application") version "8.8.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.multiplatform") version "2.1.0" apply false
    id("org.jetbrains.compose") version "1.9.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
    id("com.google.devtools.ksp") version "2.1.0-1.0.29" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
```

变更说明：新增 `org.jetbrains.kotlin.multiplatform`、`org.jetbrains.compose`；移除 `com.google.dagger.hilt.android`。

---

### 任务 1.3：更新 settings.gradle.kts

**文件：**
- 修改：`settings.gradle.kts`

- [ ] **步骤 1：添加 shared 和 desktopApp 模块，保留 app 模块暂时**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://github.com/psiegman/mvn-repo/raw/master/releases") }
    }
}

rootProject.name = "SimpleBook"
include(":shared")
include(":androidApp")
include(":desktopApp")
```

注意：暂时不删除 `:app`，Phase 4 完成后再移除。

---

### 任务 1.4：创建 shared 模块 build.gradle.kts

**文件：**
- 创建：`shared/build.gradle.kts`

- [ ] **步骤 1：编写 shared 模块构建配置**

```kotlin
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            // Navigation (CMP)
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0-alpha01")

            // Lifecycle ViewModel (CMP)
            implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0-alpha01")

            // Room KMP
            implementation("androidx.room:room-runtime:2.7.0")
            implementation("androidx.room:room-ktx:2.7.0")

            // Koin
            implementation("io.insert-koin:koin-core:4.0.4")
            implementation("io.insert-koin:koin-compose:4.0.4")
            implementation("io.insert-koin:koin-compose-viewmodel:4.0.4")
            implementation("io.insert-koin:koin-compose-viewmodel-navigation:4.0.4")

            // Google Drive REST API (portable)
            implementation("com.google.apis:google-api-services-drive:v3-rev20250511-2.0.0")
            implementation("com.google.api-client:google-api-client:2.7.2")
            implementation("com.google.http-client:google-http-client-gson:1.46.3")

            // Gson
            implementation("com.google.code.gson:gson:2.12.1")

            // Coroutines
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            // EPUB
            implementation("nl.siegmann.epublib:epublib-core:3.1") {
                exclude(group = "org.slf4j")
                exclude(group = "xmlpull")
            }

            // Encoding detection
            implementation("com.github.albfernandez:juniversalchardet:2.4.0")

            // Coil (CMP)
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")
        }

        androidMain.dependencies {
            // Android-specific Room driver
            implementation("androidx.sqlite:sqlite-framework:2.5.0")

            // Android Google Sign-In
            implementation("com.google.android.gms:play-services-auth:21.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

            // Android DataStore
            implementation("androidx.datastore:datastore-preferences:1.1.0")

            // Android Core
            implementation("androidx.core:core-ktx:1.13.0")
            implementation("androidx.activity:activity-compose:1.9.0")

            // SLF4J Android
            implementation("org.slf4j:slf4j-android:1.7.25")
        }

        desktopMain.dependencies {
            // Desktop Room driver (JDBC SQLite)
            implementation("org.xerial:sqlite-jdbc:3.47.2.0")
            implementation("androidx.sqlite:sqlite-bundled:2.5.0")

            // SLF4J Simple for desktop
            implementation("org.slf4j:slf4j-simple:1.7.36")

            // Compose Desktop specific
            implementation(compose.desktop.currentOs)
        }
    }
}

android {
    namespace = "com.ebookreader.simplebook.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

---

### 任务 1.5：创建 androidApp 模块

**文件：**
- 创建：`androidApp/build.gradle.kts`
- 创建：`androidApp/src/main/AndroidManifest.xml`
- 创建：`androidApp/src/main/kotlin/com/ebookreader/simplebook/SimpleBookApp.kt`

- [ ] **步骤 1：创建 androidApp/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.ebookreader.simplebook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ebookreader.simplebook"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.8.6"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.activity:activity-compose:1.9.0")
}
```

注意：不再需要 Hilt 插件。Android 专属依赖在这里，共享依赖在 shared 模块。

- [ ] **步骤 2：创建 AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:name=".SimpleBookApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="SimpleBook"
        android:supportsRtl="true"
        android:theme="@style/Theme.SimpleBook">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service
            android:name="com.ebookreader.simplebook.domain.service.SyncForegroundService"
            android:foregroundServiceType="dataSync"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **步骤 3：创建 androidApp 的 SimpleBookApp.kt（Koin 初始化）**

```kotlin
package com.ebookreader.simplebook

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SimpleBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SimpleBookApp)
            modules(
                org.koin.core.module.Module /* 将在 Phase 3 定义 */
            )
        }
    }
}
```

注意：此文件会在 Phase 3 完成 DI 迁移后补充完整的 Koin 模块引用。

- [ ] **步骤 4：复制资源文件**

```bash
cp -r app/src/main/res androidApp/src/main/res
cp -r app/src/main/mipmap-* androidApp/src/main/ 2>/dev/null || true
```

确保 Android 资源（图标、字符串等）在 androidApp 模块中可用。

---

### 任务 1.6：创建 desktopApp 模块

**文件：**
- 创建：`desktopApp/build.gradle.kts`

- [ ] **步骤 1：创建 desktopApp/build.gradle.kts**

```kotlin
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

compose.desktop {
    applications {
        mainClass = "com.ebookreader.simplebook.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "SimpleBook"
            packageVersion = "0.8.6"
            macOS {
                bundleID = "com.simplebook.desktop"
                minimumSystemVersion = "12.0"
            }
            windows {
                menuGroup = "SimpleBook"
                upgradeUuid = "550e8400-e29b-41d4-a716-446655440000"
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
}
```

- [ ] **步骤 2：创建最小化 desktopApp Main.kt（占位，Phase 5 填充）**

创建文件 `desktopApp/src/jvmMain/kotlin/com/ebookreader/simplebook/Main.kt`：

```kotlin
package com.ebookreader.simplebook

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "SimpleBook",
        state = rememberWindowState(width = 1200.dp, height = 800.dp)
    ) {
        // Phase 5 填充：加载共享 UI
    }
}
```

---

### 任务 1.7：验证 Gradle 同步

- [ ] **步骤 1：运行 Gradle sync**

```bash
cd /Users/pan/Workspace/Work_space/Ebookreader
./gradlew :androidApp:assembleDebug --dry-run
./gradlew :desktopApp:run --dry-run
```

预期：两个任务都能成功 resolve（dry-run 不实际编译），确认模块结构和依赖配置正确。

如果 Gradle sync 失败，检查：
- Compose Multiplatform 插件版本与 Kotlin 版本兼容性
- KSP 配置在 shared 模块是否正确应用
- Repository 声明是否完整

- [ ] **步骤 2：Commit 骨架**

```bash
git add -A
git commit -m "feat(kmp): scaffold KMP multi-module structure — shared + androidApp + desktopApp"
```

---

## Phase 2：数据层迁移

### 任务 2.1：创建 shared 模块目录结构并移动 Entity

**文件：**
- 移动：`app/src/main/java/.../data/local/entity/*.kt` → `shared/src/commonMain/kotlin/.../data/local/entity/`

- [ ] **步骤 1：创建目标目录并移动 Entity 文件**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/local/entity

for f in BookEntity.kt ReadingProgressEntity.kt BookmarkEntity.kt \
         HighlightEntity.kt NoteEntity.kt FolderEntity.kt SyncLogEntity.kt; do
  git mv app/src/main/java/com/ebookreader/simplebook/data/local/entity/$f \
         shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/local/entity/$f
done
```

- [ ] **步骤 2：验证 Entity 文件不需要改动**

Entity 文件使用 `@Entity`、`@PrimaryKey`、`@ColumnInfo`、`@ForeignKey`、`@Index` 注解，这些在 Room KMP 中完全兼容。无需改动任何内容。

---

### 任务 2.2：移动 DAO 文件到 commonMain

**文件：**
- 移动：`app/src/main/java/.../data/local/dao/*.kt` → `shared/src/commonMain/kotlin/.../data/local/dao/`

- [ ] **步骤 1：移动所有 DAO 文件**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/local/dao

for f in BookDao.kt ReadingProgressDao.kt BookmarkDao.kt HighlightDao.kt \
         NoteDao.kt SyncLogDao.kt FolderDao.kt; do
  git mv app/src/main/java/com/ebookreader/simplebook/data/local/dao/$f \
         shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/local/dao/$f
done
```

- [ ] **步骤 2：验证 DAO 文件兼容性**

DAO 使用 `@Dao`、`@Query`、`@Insert`、`@Update`、`@Delete` 注解和 `Flow`/`suspend` 函数，全部兼容 Room KMP。无需改动。

---

### 任务 2.3：迁移 SimpleBookDatabase 到 Room KMP

**文件：**
- 修改：`shared/src/commonMain/kotlin/.../data/local/SimpleBookDatabase.kt`（从 app 移入并重构）

- [ ] **步骤 1：移动数据库文件**

```bash
git mv app/src/main/java/com/ebookreader/simplebook/data/local/SimpleBookDatabase.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/local/SimpleBookDatabase.kt
```

- [ ] **步骤 2：重构数据库类，去掉 Android Context 依赖**

关键变更：
1. 迁移对象中 `SupportSQLiteDatabase` 改为 Room KMP 兼容类型
2. 通过 expect 函数获取 `RoomDatabase.Builder`
3. 迁移对象的 SQL 是标准 SQLite，可直接复用

```kotlin
package com.ebookreader.simplebook.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ebookreader.simplebook.data.local.dao.*
import com.ebookreader.simplebook.data.local.entity.*

@Database(
    entities = [
        BookEntity::class,
        ReadingProgressEntity::class,
        BookmarkEntity::class,
        HighlightEntity::class,
        NoteEntity::class,
        SyncLogEntity::class,
        FolderEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class SimpleBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun highlightDao(): HighlightDao
    abstract fun noteDao(): NoteDao
    abstract fun syncLogDao(): SyncLogDao
    abstract fun folderDao(): FolderDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 原有 SQL 保持不变 — 标准 SQLite，两端兼容
                db.execSQL("ALTER TABLE books ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE books ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE books ADD COLUMN driveFileId TEXT")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE reading_progress ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE bookmarks ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE highlights ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE highlights ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("ALTER TABLE notes ADD COLUMN syncVersion INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE notes ADD COLUMN lastSyncedAt INTEGER")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS conflict_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        bookId INTEGER NOT NULL,
                        entityType TEXT NOT NULL,
                        entityId INTEGER NOT NULL,
                        localSyncVersion INTEGER NOT NULL,
                        remoteSyncVersion INTEGER NOT NULL,
                        localData TEXT NOT NULL,
                        remoteData TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        resolvedAt INTEGER,
                        FOREIGN KEY(bookId) REFERENCES books(id) ON DELETE CASCADE
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 完整迁移 SQL — 与原文件保持一致
                // （books, reading_progress, bookmarks, highlights, notes 全部迁移到 UUID 主键）
                // 此处复制原文件中的完整 MIGRATION_2_3 SQL
                // 原文件路径: app/src/main/java/.../SimpleBookDatabase.kt
                // 迁移 SQL 是标准 SQLite，两端兼容
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
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
                db.execSQL("ALTER TABLE books ADD COLUMN folderId TEXT")
            }
        }
    }
}
```

注意：`MIGRATION_2_3` 的完整 SQL 内容需要从原文件完整复制（已在上文读取过）。SQL 是标准 SQLite，在 commonMain 中两端均可执行。但实际上 Migration 只会在 Android 端运行（现有用户升级），Desktop 端会直接创建最新 Schema。

- [ ] **步骤 3：创建 expect 函数获取 Room Builder**

创建 `shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/local/DatabaseBuilder.kt`：

```kotlin
package com.ebookreader.simplebook.data.local

import androidx.room.RoomDatabase

expect fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase>
```

- [ ] **步骤 4：创建 Android actual — DatabaseBuilder**

创建 `shared/src/androidMain/kotlin/com/ebookreader/simplebook/data/local/DatabaseBuilder.kt`：

```kotlin
package com.ebookreader.simplebook.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import org.koin.mp.KoinPlatform

actual fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase> {
    val context = KoinPlatform.getKoin().get<Context>()
    return Room.databaseBuilder(
        context,
        SimpleBookDatabase::class.java,
        "simplebook.db"
    )
}
```

- [ ] **步骤 5：创建 Desktop actual — DatabaseBuilder**

创建 `shared/src/desktopMain/kotlin/com/ebookreader/simplebook/data/local/DatabaseBuilder.kt`：

```kotlin
package com.ebookreader.simplebook.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

actual fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase> {
    val dbDir = File(System.getProperty("user.home"), "Library/SimpleBook/database")
    dbDir.mkdirs()
    return Room.databaseBuilder<SimpleBookDatabase>(
        name = File(dbDir, "simplebook.db").absolutePath
    )
}
```

注意：macOS 路径用 `~/Library/SimpleBook/database/`，Windows 路径在 future 阶段通过 PlatformPath expect/actual 切换。

---

### 任务 2.4：创建平台路径抽象

**文件：**
- 创建：`shared/src/commonMain/kotlin/.../platform/PlatformPath.kt`
- 创建：`shared/src/androidMain/kotlin/.../platform/PlatformPath.kt`
- 创建：`shared/src/desktopMain/kotlin/.../platform/PlatformPath.kt`

- [ ] **步骤 1：定义 expect**

```kotlin
// commonMain
package com.ebookreader.simplebook.platform

expect fun getBooksDir(): String
expect fun getDatabaseDir(): String
expect fun getCacheDir(): String
```

- [ ] **步骤 2：Android actual**

```kotlin
// androidMain
package com.ebookreader.simplebook.platform

import android.content.Context
import org.koin.mp.KoinPlatform

actual fun getBooksDir(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return java.io.File(context.filesDir, "books").absolutePath
}

actual fun getDatabaseDir(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.getDatabasePath("simplebook.db").parent!!
}

actual fun getCacheDir(): String {
    val context = KoinPlatform.getKoin().get<Context>()
    return context.cacheDir.absolutePath
}
```

- [ ] **步骤 3：Desktop actual**

```kotlin
// desktopMain
package com.ebookreader.simplebook.platform

import java.io.File

actual fun getBooksDir(): String {
    val dir = File(System.getProperty("user.home"), "Library/SimpleBook/books")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun getDatabaseDir(): String {
    val dir = File(System.getProperty("user.home"), "Library/SimpleBook/database")
    dir.mkdirs()
    return dir.absolutePath
}

actual fun getCacheDir(): String {
    val dir = File(System.getProperty("user.home"), "Library/SimpleBook/cache")
    dir.mkdirs()
    return dir.absolutePath
}
```

---

### 任务 2.5：移动 Repository 文件到 commonMain

**文件：**
- 移动：`app/src/main/java/.../data/repository/*.kt` → `shared/src/commonMain/kotlin/.../data/repository/`

- [ ] **步骤 1：移动所有 Repository 文件**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/repository

for f in BookRepository.kt BookRepositoryImpl.kt BookmarkRepository.kt \
         BookmarkRepositoryImpl.kt HighlightRepository.kt HighlightRepositoryImpl.kt \
         NoteRepository.kt NoteRepositoryImpl.kt ReadingProgressRepository.kt \
         ReadingProgressRepositoryImpl.kt FolderRepository.kt FolderRepositoryImpl.kt \
         SyncLogRepository.kt; do
  git mv app/src/main/java/com/ebookreader/simplebook/data/repository/$f \
         shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/repository/$f
done
```

注意：如果实际文件数量不同，需要先 `ls` 确认。移动后检查 Repository 中是否有 Android Context 依赖 — 大部分 Repository 是纯 Room DAO 操作，应该没有。

- [ ] **步骤 2：检查并修复 Context 引用**

如果任何 Repository 引用了 `android.content.Context`，需要替换为 `platform.PlatformPath.getBooksDir()`。逐个文件检查：

```bash
grep -rn "android.content.Context\|context\." shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/repository/
```

如果有匹配，将 Context 依赖替换为 PlatformPath 调用。

---

### 任务 2.6：移动 Domain 层文件到 commonMain

**文件：**
- 移动：`domain/model/*.kt` → `shared/src/commonMain/...`
- 移动：`domain/service/*.kt` → `shared/src/commonMain/...`（除 SyncForegroundService）

- [ ] **步骤 1：移动 domain/model**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model
git mv app/src/main/java/com/ebookreader/simplebook/domain/model/*.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/model/
```

- [ ] **步骤 2：移动 domain/service（排除 SyncForegroundService）**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service

for f in BookService.kt BookmarkService.kt FileImportService.kt FolderService.kt \
         HighlightService.kt NoteService.kt ReadingService.kt SyncService.kt; do
  git mv app/src/main/java/com/ebookreader/simplebook/domain/service/$f \
         shared/src/commonMain/kotlin/com/ebookreader/simplebook/domain/service/$f
done
```

注意：`SyncForegroundService.kt` 留在 Android — 它是 Android Foreground Service，不能跨平台。

- [ ] **步骤 3：处理 SyncService 中的 Android 依赖**

SyncService 当前依赖 `android.content.Context`（用于 SharedPreferences、filesDir 等）。需要重构：

1. **SharedPreferences** → 通过 expect/actual 抽象为 `SyncPreferences`：
   - commonMain: `expect class SyncPreferences { fun getLong(key: String): Long?; fun putLong(key: String, value: Long); fun getStringSet(key: String): Set<String>?; fun putStringSet(key: String, value: Set<String>) }`
   - androidMain: 包装 SharedPreferences
   - desktopMain: 使用 `java.util.Properties` 文件

2. **`context.filesDir`** → 替换为 `platform.getBooksDir()`

3. **`android.util.Log`** → 替换为 expect/actual 的 `AppLog` 或直接用 `println`

这些依赖较多，SyncService 的完整重构在 Phase 6（Google Drive 桌面端）中处理。Phase 2 先确保文件移动到位，代码编译问题在后续 Phase 逐步修复。

- [ ] **步骤 4：移动 SyncStatus 到独立文件**

从 SyncService.kt 中提取 `SyncStatus` 和 `ImportStatus` sealed class 到独立文件：

创建 `shared/src/commonMain/kotlin/.../domain/service/SyncStatus.kt`：

```kotlin
package com.ebookreader.simplebook.domain.service

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Success : SyncStatus()
}

sealed class ImportStatus {
    data object Idle : ImportStatus()
    data object Importing : ImportStatus()
    data class Success(val count: Int) : ImportStatus()
    data class Error(val message: String) : ImportStatus()
}
```

---

### 任务 2.7：移动 Parser 文件到 commonMain

**文件：**
- 移动：`data/parser/*.kt` → `shared/src/commonMain/...`

- [ ] **步骤 1：移动 parser 文件**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser
git mv app/src/main/java/com/ebookreader/simplebook/data/parser/*.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/
```

- [ ] **步骤 2：检查 Parser 中的 Android 依赖**

```bash
grep -rn "android\." shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/parser/
```

EpubParser 和 TxtParser 可能使用 `android.util.Log` 或 Android 文件 API。如果有：
- `android.util.Log` → 替换为 `println` 或 expect/actual logger
- Android 文件 API → 使用 `java.io.File`（JVM 上可用）

---

### 任务 2.8：验证数据层编译

- [ ] **步骤 1：尝试编译 shared 模块**

```bash
./gradlew :shared:compileKotlinDesktop --no-daemon 2>&1 | head -50
```

预期：可能有一些未解决的 expect/actual 或 import 错误。逐个修复：
- 缺少 expect/actual 实现的函数 → 添加占位 actual
- 错误的 import 路径 → 修正
- Android 特有 API 引用 → 替换为跨平台等价物

- [ ] **步骤 2：Commit 数据层迁移**

```bash
git add -A
git commit -m "feat(kmp): migrate data layer — entities, DAOs, Room KMP, repositories, domain"
```

---

## Phase 3：DI 迁移（Hilt → Koin）

### 任务 3.1：添加 Koin 依赖并创建共享 DI 模块

**文件：**
- 创建：`shared/src/commonMain/kotlin/.../di/AppModule.kt`
- 创建：`shared/src/commonMain/kotlin/.../di/DataModule.kt`
- 创建：`shared/src/commonMain/kotlin/.../di/PlatformModule.kt`

- [ ] **步骤 1：创建 AppModule — ViewModel 和 Service 声明**

```kotlin
// shared/src/commonMain/kotlin/com/ebookreader/simplebook/di/AppModule.kt
package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.ui.booklist.BookListViewModel
import com.ebookreader.simplebook.ui.bookmark.BookmarkViewModel
import com.ebookreader.simplebook.ui.collection.CollectionViewModel
import com.ebookreader.simplebook.ui.note.NoteViewModel
import com.ebookreader.simplebook.ui.reader.ReaderViewModel
import com.ebookreader.simplebook.ui.settings.SettingsViewModel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::BookListViewModel)
    viewModelOf(::BookmarkViewModel)
    viewModelOf(::CollectionViewModel)
    viewModelOf(::NoteViewModel)
    viewModelOf(::ReaderViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SyncViewModel)
}
```

注意：ViewModel 的构造函数需要去掉 `@Inject` 注解，详见任务 3.3。

- [ ] **步骤 2：创建 DataModule — Database, DAO, Repository, Gson**

```kotlin
// shared/src/commonMain/kotlin/com/ebookreader/simplebook/di/DataModule.kt
package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.SimpleBookDatabase
import com.ebookreader.simplebook.data.local.dao.*
import com.ebookreader.simplebook.data.repository.*
import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.domain.service.SyncService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    // Database
    single { getRoomDatabaseBuilder().addMigrations(
        SimpleBookDatabase.MIGRATION_1_2,
        SimpleBookDatabase.MIGRATION_2_3,
        SimpleBookDatabase.MIGRATION_3_4
    ).fallbackToDestructiveMigration().build() }

    // DAOs
    single { get<SimpleBookDatabase>().bookDao() }
    single { get<SimpleBookDatabase>().readingProgressDao() }
    single { get<SimpleBookDatabase>().bookmarkDao() }
    single { get<SimpleBookDatabase>().highlightDao() }
    single { get<SimpleBookDatabase>().noteDao() }
    single { get<SimpleBookDatabase>().syncLogDao() }
    single { get<SimpleBookDatabase>().folderDao() }

    // Repositories
    singleOf(::BookRepositoryImpl)
    single<BookRepository> { get<BookRepositoryImpl>() }
    singleOf(::BookmarkRepositoryImpl)
    single<BookmarkRepository> { get<BookmarkRepositoryImpl>() }
    singleOf(::HighlightRepositoryImpl)
    single<HighlightRepository> { get<HighlightRepositoryImpl>() }
    singleOf(::NoteRepositoryImpl)
    single<NoteRepository> { get<NoteRepositoryImpl>() }
    singleOf(::ReadingProgressRepositoryImpl)
    single<ReadingProgressRepository> { get<ReadingProgressRepositoryImpl>() }
    singleOf(::FolderRepositoryImpl)
    single<FolderRepository> { get<FolderRepositoryImpl>() }

    // Parsers
    singleOf(::EpubParser)
    singleOf(::TxtParser)

    // Gson
    single { GsonBuilder().create() }

    // Settings (平台特有 — 通过 expect/actual 注入)
    single { getSettingsDataStore() }

    // SyncService
    singleOf(::SyncService)
}
```

- [ ] **步骤 3：创建 PlatformModule — expect 声明**

```kotlin
// shared/src/commonMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt
package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.SettingsDataStore
import org.koin.core.module.Module

expect val platformModule: Module

// SettingsDataStore 因平台实现不同，需要 expect
expect fun getSettingsDataStore(): SettingsDataStore
```

- [ ] **步骤 4：Android actual — PlatformModule**

```kotlin
// shared/src/androidMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt
package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.data.remote.AuthManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule: Module = module {
    singleOf(::AuthManager)
    single { GoogleDriveClient(androidContext(), get()) }
}

actual fun getSettingsDataStore(): SettingsDataStore {
    return SettingsDataStore(org.koin.mp.KoinPlatform.getKoin().get())
}
```

- [ ] **步骤 5：Desktop actual — PlatformModule**

```kotlin
// shared/src/desktopMain/kotlin/com/ebookreader/simplebook/di/PlatformModule.kt
package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.SettingsDataStore
import com.ebookreader.simplebook.platform.AuthProvider
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

actual val platformModule: Module = module {
    singleOf(::AuthProvider)
    singleOf(::GoogleDriveClient)
}

actual fun getSettingsDataStore(): SettingsDataStore {
    return SettingsDataStore() // Desktop 版不需要 Context
}
```

---

### 任务 3.2：移除 Hilt 注解和依赖

**文件：**
- 修改：所有 ViewModel 文件（7 个）
- 修改：SyncService.kt, GoogleDriveClient.kt, AuthManager.kt 等 Service 文件
- 删除：原 Hilt DI 模块文件

- [ ] **步骤 1：从所有 ViewModel 移除 @HiltViewModel 和 @Inject**

对每个 ViewModel 文件执行以下替换：

| 移除 | 保留 |
|------|------|
| `@HiltViewModel` 注解 | 构造函数参数不变 |
| `@Inject constructor` → `constructor` | 参数列表不变 |
| `import dagger.hilt.android.lifecycle.HiltViewModel` | 删除 |

示例 — BookListViewModel.kt 变更：

```kotlin
// Before:
@HiltViewModel
class BookListViewModel @Inject constructor(
    private val bookService: BookService,
    ...
) : ViewModel()

// After:
class BookListViewModel(
    private val bookService: BookService,
    ...
) : ViewModel()
```

对所有 7 个 ViewModel 重复此操作：
- `BookListViewModel`, `BookmarkViewModel`, `CollectionViewModel`
- `NoteViewModel`, `ReaderViewModel`, `SettingsViewModel`, `SyncViewModel`

- [ ] **步骤 2：处理 SettingsViewModel 和 SyncViewModel 的 Android 依赖**

`SettingsViewModel` 当前构造参数包含 `private val application: Application`。替换方案：
- 通过 Koin 注入所需服务而非 Application 对象
- 或者使用 expect/actual 抽象

`SyncViewModel` 当前是 `AndroidViewModel(application)`。替换为普通 `ViewModel`，通过构造函数注入所需依赖。

- [ ] **步骤 3：移除所有文件中的 javax.inject 和 Hilt import**

```bash
find shared/src/commonMain -name "*.kt" -exec sed -i '' '/import javax.inject/d' {} +
find shared/src/commonMain -name "*.kt" -exec sed -i '' '/import dagger.hilt/d' {} +
find shared/src/commonMain -name "*.kt" -exec sed -i '' '/@Inject/d' {} +
find shared/src/commonMain -name "*.kt" -exec sed -i '' '/@Singleton/d' {} +
```

- [ ] **步骤 4：删除原 Hilt DI 模块**

```bash
rm app/src/main/java/com/ebookreader/simplebook/di/DatabaseModule.kt
rm app/src/main/java/com/ebookreader/simplebook/di/RepositoryModule.kt
rm app/src/main/java/com/ebookreader/simplebook/di/SyncModule.kt
```

---

### 任务 3.3：更新 androidApp 入口使用 Koin

**文件：**
- 修改：`androidApp/src/main/kotlin/.../SimpleBookApp.kt`

- [ ] **步骤 1：更新 SimpleBookApp.kt**

```kotlin
package com.ebookreader.simplebook

import android.app.Application
import com.ebookreader.simplebook.di.appModule
import com.ebookreader.simplebook.di.dataModule
import com.ebookreader.simplebook.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SimpleBookApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@SimpleBookApp)
            modules(appModule, dataModule, platformModule)
        }
    }
}
```

---

### 任务 3.4：验证 DI 迁移

- [ ] **步骤 1：编译 Android 端**

```bash
./gradlew :androidApp:assembleDebug 2>&1 | tail -30
```

预期：可能有一些编译错误需要逐个修复（缺少 import、未解决的依赖等）。修复后重试直到编译通过。

- [ ] **步骤 2：运行 Android 应用验证基本功能**

在模拟器或真机上安装并运行，验证：
- 应用正常启动
- 书架显示正常
- 数据库操作正常

- [ ] **步骤 3：Commit DI 迁移**

```bash
git add -A
git commit -m "feat(kmp): migrate DI from Hilt to Koin"
```

---

## Phase 4：UI 迁移

### 任务 4.1：移动 Theme 文件到 commonMain

**文件：**
- 移动：`ui/theme/*.kt` → `shared/src/commonMain/...`

- [ ] **步骤 1：移动 Theme 文件**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/theme
git mv app/src/main/java/com/ebookreader/simplebook/ui/theme/*.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/theme/
```

Theme 文件使用标准 Compose Material 3 API，与 CMP 完全兼容。

---

### 任务 4.2：移动 UI Components 到 commonMain

**文件：**
- 移动：`ui/components/*.kt` → `shared/src/commonMain/...`

- [ ] **步骤 1：移动组件文件**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/components
git mv app/src/main/java/com/ebookreader/simplebook/ui/components/*.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/components/
```

- [ ] **步骤 2：检查并替换 Android 特有 import**

```bash
grep -rn "import android\." shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/
```

可能的替换：
- `androidx.compose.*` → 保持不变（CMP 兼容）
- `android.util.Log` → `println`
- `android.content.*` → 通过 expect/actual 或 Koin 注入

---

### 任务 4.3：移动 Navigation 到 commonMain

**文件：**
- 移动：`ui/navigation/*.kt` → `shared/src/commonMain/...`

- [ ] **步骤 1：移动 Screen.kt 和 SimpleBookNavHost.kt**

```bash
mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/navigation
git mv app/src/main/java/com/ebookreader/simplebook/ui/navigation/*.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/navigation/
```

- [ ] **步骤 2：替换 Navigation import**

如果使用 `androidx.navigation:navigation-compose`，替换为 CMP 版本：
- `import androidx.navigation.*` → 保持（CMP 会映射这些包）
- 确保 shared/build.gradle.kts 中使用 CMP navigation 依赖

---

### 任务 4.4：移动 Screen + ViewModel 到 commonMain

**文件：**
- 移动：所有 Screen 和 ViewModel 文件

- [ ] **步骤 1：移动所有 Screen/ViewModel 对**

```bash
# 创建目录
for dir in booklist bookmark collection note reader settings sync; do
  mkdir -p shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/$dir
done

# 移动文件
for dir in booklist bookmark collection note reader settings sync; do
  git mv app/src/main/java/com/ebookreader/simplebook/ui/$dir/*.kt \
         shared/src/commonMain/kotlin/com/ebookreader/simplebook/ui/$dir/
done
```

- [ ] **步骤 2：修复 hiltViewModel() 调用**

Screen 文件中使用 `hiltViewModel()` 获取 ViewModel。替换为 Koin 等价物：

```kotlin
// Before:
import androidx.hilt.navigation.compose.hiltViewModel
val viewModel: BookListViewModel = hiltViewModel()

// After:
import org.koin.compose.viewmodel.koinViewModel
val viewModel: BookListViewModel = koinViewModel()
```

对所有 Screen 文件执行此替换。

- [ ] **步骤 3：修复 ReaderScreen 的平台特定代码**

ReaderScreen 可能使用 Android 特有的文本渲染或 WebView。检查并适配：
- 如果使用 Compose Canvas/Text → 直接兼容
- 如果使用 Android WebView → 需要 expect/actual 或用 CMP Text 替代

---

### 任务 4.5：移动 Remote 数据文件

**文件：**
- 移动：`data/remote/SyncMetadata.kt` → commonMain
- 保留/重构：`AuthManager.kt` 和 `GoogleDriveClient.kt`

- [ ] **步骤 1：移动 SyncMetadata.kt**

```bash
git mv app/src/main/java/com/ebookreader/simplebook/data/remote/SyncMetadata.kt \
       shared/src/commonMain/kotlin/com/ebookreader/simplebook/data/remote/SyncMetadata.kt
```

- [ ] **步骤 2：创建 DriveCredential expect/actual**

```kotlin
// commonMain — data/remote/DriveCredential.kt
package com.ebookreader.simplebook.data.remote

expect class DriveCredential {
    fun initialize(request: com.google.api.client.http.HttpRequest)
}
```

- [ ] **步骤 3：重构 GoogleDriveClient 为 expect/actual**

Android 版保持使用 `GoogleAccountCredential`，Desktop 版使用 `GoogleCredential`（基于 OAuth 2.0 token）。

核心思路：将 GoogleDriveClient 中的 `drive` 属性构造逻辑通过 expect/actual 分离。REST API 调用方法（uploadFile, downloadFile 等）全部在 commonMain 中共享。

此重构在 Phase 6 详细展开。Phase 4 先将 GoogleDriveClient 保留在 androidMain 中，确保 Android 端正常工作。

---

### 任务 4.6：创建 androidApp 的 MainActivity

**文件：**
- 创建：`androidApp/src/main/kotlin/.../MainActivity.kt`

- [ ] **步骤 1：从原 MainActivity 提取，替换 Hilt 为 Koin**

```kotlin
package com.ebookreader.simplebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.ebookreader.simplebook.ui.navigation.SimpleBookNavHost
import com.ebookreader.simplebook.ui.theme.SimpleBookTheme
import org.koin.compose.viewmodel.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = koinViewModel()
            val settings by settingsViewModel.settings.collectAsState()
            SimpleBookTheme(readerTheme = settings.theme) {
                val navController = rememberNavController()
                val syncViewModel: SyncViewModel = koinViewModel()
                SimpleBookNavHost(
                    navController = navController,
                    syncViewModel = syncViewModel,
                    signInLauncher = { /* Phase 6 实现 */ }
                )
            }
        }
    }
}
```

注意：Splash screen、权限请求、生命周期同步触发等 Android 特有逻辑保留在此文件中。从原 `MainActivity.kt` 逐步迁移。

---

### 任务 4.7：验证 UI 迁移 — Android 端编译

- [ ] **步骤 1：编译 Android 端**

```bash
./gradlew :androidApp:assembleDebug 2>&1 | tail -50
```

逐个修复编译错误。常见问题：
- `hiltViewModel()` 未替换 → 改为 `koinViewModel()`
- `@AndroidEntryPoint` 未移除 → 删除
- Android Context 引用 → 通过 Koin 注入
- `import androidx.hilt.*` → 删除

- [ ] **步骤 2：在 Android 模拟器上验证完整 UI 流程**

手动测试：书架浏览 → 打开书籍 → 阅读器 → 书签 → 笔记 → 设置

- [ ] **步骤 3：Commit UI 迁移**

```bash
git add -A
git commit -m "feat(kmp): migrate UI layer — screens, components, theme, navigation to shared"
```

---

## Phase 5：Desktop 入口

### 任务 5.1：实现 Desktop Main.kt

**文件：**
- 修改：`desktopApp/src/jvmMain/kotlin/.../Main.kt`

- [ ] **步骤 1：编写完整的 Desktop 入口**

```kotlin
package com.ebookreader.simplebook

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.ebookreader.simplebook.di.appModule
import com.ebookreader.simplebook.di.dataModule
import com.ebookreader.simplebook.di.platformModule
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(appModule, dataModule, platformModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "SimpleBook",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            minimumSize = DpSize(800.dp, 600.dp)
        )
    ) {
        App()
    }
}
```

- [ ] **步骤 2：创建共享 App composable**

在 `shared/src/commonMain/kotlin/.../App.kt` 创建共享 UI 入口：

```kotlin
package com.ebookreader.simplebook

import androidx.compose.runtime.*
import androidx.navigation.compose.rememberNavController
import com.ebookreader.simplebook.ui.navigation.SimpleBookNavHost
import com.ebookreader.simplebook.ui.theme.SimpleBookTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun App() {
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    SimpleBookTheme(readerTheme = settings.theme) {
        val navController = rememberNavController()
        SimpleBookNavHost(
            navController = navController,
            syncViewModel = null,
            signInLauncher = null
        )
    }
}
```

注意：Android 端的 MainActivity 也可以调用 `App()` 来复用 UI。这样两端共享同一个 UI 入口点。

---

### 任务 5.2：创建 Desktop SettingsDataStore

**文件：**
- 创建：`shared/src/desktopMain/kotlin/.../data/local/SettingsDataStore.kt`

- [ ] **步骤 1：实现基于 Properties 文件的 SettingsDataStore**

Desktop 端不能用 Android DataStore，改用 `java.util.Properties`：

```kotlin
package com.ebookreader.simplebook.data.local

import com.ebookreader.simplebook.domain.model.LayoutMode
import com.ebookreader.simplebook.domain.model.ReaderSettings
import com.ebookreader.simplebook.domain.model.ReaderTheme
import com.ebookreader.simplebook.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

class SettingsDataStore {
    private val propsFile = File(
        System.getProperty("user.home"), "Library/SimpleBook/settings.properties"
    )
    private val props = Properties().apply {
        if (propsFile.exists()) propsFile.inputStream().use { load(it) }
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: Flow<ReaderSettings> = _settings.asStateFlow()

    private fun loadSettings(): ReaderSettings {
        return ReaderSettings(
            fontSize = props.getProperty("font_size", "16").toFloat(),
            lineHeight = props.getProperty("line_height", "1.5").toFloat(),
            theme = ReaderTheme.fromKey(props.getProperty("theme", "default_white")),
            language = props.getProperty("language", "zh"),
            layoutMode = LayoutMode.fromKey(props.getProperty("layout_mode")),
            sortOrder = SortOrder.fromKey(props.getProperty("sort_order"))
        )
    }

    private fun save() {
        propsFile.parentFile.mkdirs()
        propsFile.outputStream().use { props.store(it, null) }
    }

    suspend fun updateFontSize(size: Float) {
        props["font_size"] = size.toString()
        save(); _settings.value = loadSettings()
    }

    suspend fun updateLineHeight(height: Float) {
        props["line_height"] = height.toString()
        save(); _settings.value = loadSettings()
    }

    suspend fun updateTheme(theme: ReaderTheme) {
        props["theme"] = theme.key
        save(); _settings.value = loadSettings()
    }

    suspend fun updateLanguage(language: String) {
        props["language"] = language
        save(); _settings.value = loadSettings()
    }

    suspend fun updateLayoutMode(layoutMode: LayoutMode) {
        props["layout_mode"] = layoutMode.key
        save(); _settings.value = loadSettings()
    }

    suspend fun updateSortOrder(sortOrder: SortOrder) {
        props["sort_order"] = sortOrder.key
        save(); _settings.value = loadSettings()
    }
}
```

---

### 任务 5.3：验证 Desktop 端首次启动

- [ ] **步骤 1：编译 Desktop 应用**

```bash
./gradlew :desktopApp:compileKotlinDesktop 2>&1 | tail -30
```

修复编译错误直到通过。

- [ ] **步骤 2：运行 Desktop 应用**

```bash
./gradlew :desktopApp:run
```

预期：窗口弹出，显示书架界面（空书架因为数据库是新的）。验证：
- 窗口正常显示 1200x800
- 主题渲染正确
- 导航正常工作（书架 → 设置 → 返回）
- 数据库文件在 `~/Library/SimpleBook/` 创建

- [ ] **步骤 3：Commit Desktop 入口**

```bash
git add -A
git commit -m "feat(desktop): add desktop app entry point — first launch verified"
```

---

## Phase 6：Google Drive 桌面端

### 任务 6.1：实现 Desktop OAuth 2.0 PKCE 认证

**文件：**
- 创建：`shared/src/desktopMain/kotlin/.../platform/AuthProvider.kt`
- 创建：`shared/src/desktopMain/kotlin/.../platform/DesktopOAuthServer.kt`
- 创建：`shared/src/desktopMain/kotlin/.../platform/TokenStorage.kt`

- [ ] **步骤 1：创建 AuthProvider expect 声明**

在 `shared/src/commonMain/kotlin/.../platform/AuthProvider.kt`：

```kotlin
package com.ebookreader.simplebook.platform

expect class AuthProvider() {
    val isSignedIn: Boolean
    val userEmail: String?
    suspend fun signIn(): Result<String>   // 返回 access token
    fun signOut()
}
```

- [ ] **步骤 2：Android actual — 包装现有 AuthManager**

在 `shared/src/androidMain/kotlin/.../platform/AuthProvider.kt`：

```kotlin
package com.ebookreader.simplebook.platform

import com.ebookreader.simplebook.data.remote.AuthManager

actual class AuthProvider actual constructor() {
    private val authManager: AuthManager by lazy {
        org.koin.mp.KoinPlatform.getKoin().get<AuthManager>()
    }

    actual val isSignedIn: Boolean get() = authManager.isSignedIn
    actual val userEmail: String? get() = authManager.signedInAccount.value?.email

    actual suspend fun signIn(): Result<String> {
        // Android 端通过 Activity 的 signInLauncher 处理
        // 这里返回当前 account 的 token
        val account = authManager.signedInAccount.value
            ?: return Result.failure(Exception("Not signed in"))
        return Result.success("android_session")
    }

    actual fun signOut() {
        authManager.signOut()
    }
}
```

注意：Android 端的实际 Sign-In 流程仍在 MainActivity 中通过 `signInLauncher` 触发。AuthProvider 是对现有 AuthManager 的包装。

- [ ] **步骤 3：Desktop actual — OAuth 2.0 PKCE**

在 `shared/src/desktopMain/kotlin/.../platform/AuthProvider.kt`：

```kotlin
package com.ebookreader.simplebook.platform

import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64

actual class AuthProvider actual constructor() {
    private val tokenStorage = TokenStorage()
    private var accessToken: String? = tokenStorage.getAccessToken()
    private var refreshToken: String? = tokenStorage.getRefreshToken()

    actual val isSignedIn: Boolean get() = accessToken != null
    actual val userEmail: String? get() = tokenStorage.getUserEmail()

    actual suspend fun signIn(): Result<String> {
        return try {
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)
            val state = generateState()

            val authUrl = buildString {
                append("https://accounts.google.com/o/oauth2/v2/auth?")
                append("client_id=${CLIENT_ID}")
                append("&redirect_uri=http://localhost:${PORT}")
                append("&response_type=code")
                append("&scope=${URLEncoder.encode(SCOPES, "UTF-8")}")
                append("&code_challenge=$codeChallenge")
                append("&code_challenge_method=S256")
                append("&state=$state")
            }

            // 启动本地回调服务器
            val server = DesktopOAuthServer(PORT)
            val codeFuture = server.start()

            // 打开浏览器
            Desktop.getDesktop().browse(URI(authUrl))

            // 等待回调
            val code = codeFuture.get()
            server.stop()

            // 用 code 换取 token
            exchangeCodeForToken(code, codeVerifier)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    actual fun signOut() {
        accessToken = null
        refreshToken = null
        tokenStorage.clear()
    }

    companion object {
        private const val CLIENT_ID = "YOUR_CLIENT_ID.apps.googleusercontent.com"
        private const val PORT = 8089
        private const val SCOPES = "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive"
    }
}
```

- [ ] **步骤 4：创建 DesktopOAuthServer — localhost 回调**

```kotlin
package com.ebookreader.simplebook.platform

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

class DesktopOAuthServer(private val port: Int) {
    private var server: HttpServer? = null

    fun start(): CompletableFuture<String> {
        val codeFuture = CompletableFuture<String>()
        val httpServer = HttpServer.create(InetSocketAddress(port), 0)

        httpServer.createContext("/") { exchange: HttpExchange ->
            val query = exchange.requestURI.query ?: ""
            val code = query.split("&")
                .mapNotNull { param ->
                    val parts = param.split("=", limit = 2)
                    if (parts.size == 2 && parts[0] == "code") parts[1] else null
                }
                .firstOrNull()

            val response = if (code != null) {
                codeFuture.complete(code)
                "<html><body><h2>Authorization successful!</h2><p>You can close this tab.</p></body></html>"
            } else {
                codeFuture.completeExceptionally(Exception("No authorization code received"))
                "<html><body><h2>Authorization failed.</h2></body></html>"
            }

            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }

        httpServer.executor = null
        httpServer.start()
        server = httpServer
        return codeFuture
    }

    fun stop() {
        server?.stop(0)
    }
}
```

- [ ] **步骤 5：创建 TokenStorage — 本地安全存储**

```kotlin
package com.ebookreader.simplebook.platform

import java.io.File
import java.util.Properties

class TokenStorage {
    private val file = File(System.getProperty("user.home"), "Library/SimpleBook/tokens.properties")
    private val props = Properties()

    init {
        if (file.exists()) file.inputStream().use { props.load(it) }
    }

    fun getAccessToken(): String? = props.getProperty("access_token")
    fun getRefreshToken(): String? = props.getProperty("refresh_token")
    fun getUserEmail(): String? = props.getProperty("user_email")

    fun saveTokens(accessToken: String, refreshToken: String?, email: String?) {
        props["access_token"] = accessToken
        refreshToken?.let { props["refresh_token"] = it }
        email?.let { props["user_email"] = it }
        save()
    }

    fun clear() {
        props.clear()
        save()
    }

    private fun save() {
        file.parentFile.mkdirs()
        file.outputStream().use { props.store(it, null) }
    }
}
```

注意：`CLIENT_ID` 需要在 Google Cloud Console 创建 Desktop OAuth 客户端后填入。开发阶段先用测试项目。

---

### 任务 6.2：重构 GoogleDriveClient 为跨平台

**文件：**
- 创建：`shared/src/commonMain/kotlin/.../data/remote/GoogleDriveClient.kt`（共享核心）
- 创建：`shared/src/androidMain/...`（Android credential）
- 创建：`shared/src/desktopMain/...`（Desktop credential）

- [ ] **步骤 1：提取 Drive Credential 接口**

在 `shared/src/commonMain/kotlin/.../data/remote/DriveCredential.kt`：

```kotlin
package com.ebookreader.simplebook.data.remote

import com.google.api.client.http.HttpRequest

interface DriveCredential {
    fun initialize(request: HttpRequest)
}
```

- [ ] **步骤 2：Android actual — 用 GoogleAccountCredential**

```kotlin
// androidMain — data/remote/AndroidDriveCredential.kt
package com.ebookreader.simplebook.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.HttpRequest
import com.google.api.services.drive.DriveScopes

class AndroidDriveCredential(
    context: Context,
    account: GoogleSignInAccount
) : DriveCredential {
    private val credential = GoogleAccountCredential.usingOAuth2(
        context, listOf(DriveScopes.DRIVE_APPDATA, DriveScopes.DRIVE)
    ).also { it.selectedAccount = account.account }

    override fun initialize(request: HttpRequest) {
        credential.initialize(request)
    }
}
```

- [ ] **步骤 3：Desktop actual — 用 GoogleCredential + OAuth token**

```kotlin
// desktopMain — data/remote/DesktopDriveCredential.kt
package com.ebookreader.simplebook.data.remote

import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.http.HttpRequest

class DesktopDriveCredential(
    private val accessToken: String
) : DriveCredential {
    private val credential = Credential(BearerToken.authorizationHeaderAccessMethod()).apply {
        setAccessToken(accessToken)
    }

    override fun initialize(request: HttpRequest) {
        credential.initialize(request)
    }
}
```

- [ ] **步骤 4：将 GoogleDriveClient 的 REST API 调用移到 commonMain**

关键变更：`drive` 属性的构造通过 `DriveCredential` 注入，API 方法保持不变。

```kotlin
// commonMain — data/remote/GoogleDriveClient.kt
package com.ebookreader.simplebook.data.remote

import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import com.ebookreader.simplebook.domain.model.FolderInfo
import com.ebookreader.simplebook.platform.AuthProvider
import org.koin.mp.KoinPlatform

class GoogleDriveClient(
    private val credentialProvider: () -> DriveCredential?
) {
    private val drive: Drive?
        get() = credentialProvider()?.let { cred ->
            Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            ) { request ->
                cred.initialize(request)
                request.readTimeout = 300000
                request.connectTimeout = 15000
            }.setApplicationName("SimpleBook").build()
        }

    // 所有现有 REST API 方法（uploadFile, downloadFile, createFolder 等）
    // 从原 GoogleDriveClient.kt 完整复制到这里
    // 这些方法使用标准 google-api-services-drive，不依赖 Android
    suspend fun uploadFile(...) { /* 原代码 */ }
    suspend fun downloadFile(...) { /* 原代码 */ }
    // ... 所有其他方法
}
```

DI 配置中注入 credentialProvider：
- Android: `single { GoogleDriveClient { AndroidDriveCredential(androidContext(), get<AuthManager>().signedInAccount.value!!) } }`
- Desktop: `single { GoogleDriveClient { DesktopDriveCredential(get<AuthProvider>().accessToken!!) } }`

---

### 任务 6.3：创建 SyncPreferences expect/actual

**文件：**
- 创建：commonMain expect + androidMain actual + desktopMain actual

- [ ] **步骤 1：commonMain expect**

```kotlin
package com.ebookreader.simplebook.platform

expect class SyncPreferences() {
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getStringSet(key: String): Set<String>?
    fun putStringSet(key: String, value: Set<String>)
}
```

- [ ] **步骤 2：Android actual — SharedPreferences**

```kotlin
package com.ebookreader.simplebook.platform

import android.content.Context
import org.koin.mp.KoinPlatform

actual class SyncPreferences actual constructor() {
    private val prefs by lazy {
        KoinPlatform.getKoin().get<Context>()
            .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    }

    actual fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    actual fun putLong(key: String, value: Long) = prefs.edit().putLong(key, value).apply()
    actual fun getStringSet(key: String): Set<String>? = prefs.getStringSet(key, null)
    actual fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
}
```

- [ ] **步骤 3：Desktop actual — Properties file**

```kotlin
package com.ebookreader.simplebook.platform

import java.io.File
import java.util.Properties

actual class SyncPreferences actual constructor() {
    private val file = File(System.getProperty("user.home"), "Library/SimpleBook/sync_prefs.properties")
    private val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }

    actual fun getLong(key: String, default: Long): Long = props.getProperty(key)?.toLongOrNull() ?: default
    actual fun putLong(key: String, value: Long) { props[key] = value.toString(); save() }
    actual fun getStringSet(key: String): Set<String>? = props.getProperty(key)?.split(",")?.toSet()
    actual fun putStringSet(key: String, value: Set<String>) { props[key] = value.joinToString(","); save() }

    private fun save() { file.parentFile.mkdirs(); file.outputStream().use { props.store(it, null) } }
}
```

- [ ] **步骤 4：重构 SyncService 使用 SyncPreferences**

将 SyncService 中的 `context.getSharedPreferences(...)` 替换为 Koin 注入的 `SyncPreferences`。
将 `context.filesDir` 替换为 `getBooksDir()`。
将 `android.util.Log` 替换为 `println`。

---

### 任务 6.4：验证双向同步

- [ ] **步骤 1：Android 端编译 + 测试同步**

```bash
./gradlew :androidApp:assembleDebug
```

在 Android 上测试 Google Drive 同步功能完整流程。

- [ ] **步骤 2：Desktop 端编译**

```bash
./gradlew :desktopApp:compileKotlinDesktop
```

- [ ] **步骤 3：Commit Google Drive 桌面端**

```bash
git add -A
git commit -m "feat(desktop): add OAuth 2.0 auth + cross-platform GoogleDriveClient"
```

---

## Phase 7：桌面端特性 + 打包

### 任务 7.1：实现拖放导入

**文件：**
- 创建：`shared/src/commonMain/kotlin/.../ui/components/DragDropZone.kt`

- [ ] **步骤 1：创建跨平台拖放处理**

```kotlin
package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.ebookreader.simplebook.domain.model.BookFormat
import java.io.File

@Composable
expect fun DragDropOverlay(
    onFilesDropped: (List<File>) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
)
```

- [ ] **步骤 2：Desktop actual — 使用 Modifier.onExternalDrag**

```kotlin
// desktopMain
package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

@Composable
actual fun DragDropOverlay(
    onFilesDropped: (List<File>) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            },
            target = remember {
                object : DragAndDropTarget {
                    override fun onStarted(event: DragAndDropEvent) { isDragging = true }
                    override fun onEnded(event: DragAndDropEvent) { isDragging = false }
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val files = event.awtTransferable
                            .getTransferData(DataFlavor.javaFileListFlavor)
                            as? List<File> ?: return false
                        val supported = files.filter { f ->
                            val ext = f.extension.lowercase()
                            ext == "epub" || ext == "txt"
                        }
                        if (supported.isNotEmpty()) onFilesDropped(supported)
                        return supported.isNotEmpty()
                    }
                }
            }
        )
    ) {
        content()
        if (isDragging) {
            // 显示拖放提示覆盖层
        }
    }
}
```

- [ ] **步骤 3：Android actual — 空实现**

```kotlin
// androidMain
package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import java.io.File

@Composable
actual fun DragDropOverlay(
    onFilesDropped: (List<File>) -> Unit,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    // Android 不支持桌面拖放，直接渲染内容
    Box(modifier = modifier) { content() }
}
```

- [ ] **步骤 4：在 BookListScreen 中集成拖放**

在 `BookListScreen` 的根布局上包裹 `DragDropOverlay`，传入 `onFilesDropped` 回调，调用 `FileImportService` 导入书籍。

---

### 任务 7.2：实现系统托盘

**文件：**
- 修改：`desktopApp/src/jvmMain/kotlin/.../Main.kt`

- [ ] **步骤 1：添加托盘图标**

```kotlin
// 在 Main.kt 的 application 块中添加
Tray(
    icon = painterResource("icon.png"),
    tooltip = "SimpleBook",
    menu = {
        Item("打开 SimpleBook", onClick = { /* 恢复窗口 */ })
        Separator()
        Item("退出", onClick = { exitApplication() })
    }
)
```

- [ ] **步骤 2：实现关闭行为 — 最小化到托盘**

```kotlin
var isMinimizedToTray by remember { mutableStateOf(false) }

Window(
    onCloseRequest = {
        isMinimizedToTray = true
        windowState.isMinimized = true
    },
    visible = !isMinimizedToTray,
    ...
) { App() }

// 托盘点击恢复
Tray(
    ...
    onAction = { isMinimizedToTray = false }
)
```

---

### 任务 7.3：配置 DMG 打包

**文件：**
- 修改：`desktopApp/build.gradle.kts`

- [ ] **步骤 1：完善打包配置**

```kotlin
compose.desktop {
    applications {
        mainClass = "com.ebookreader.simplebook.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "SimpleBook"
            packageVersion = "0.8.6"
            description = "A simple ebook reader"
            vendor = "SimpleBook"

            macOS {
                bundleID = "com.simplebook.desktop"
                minimumSystemVersion = "12.0"
                iconFile.set(file("icons/icon.icns"))
            }

            // Windows 预留
            windows {
                menuGroup = "SimpleBook"
                upgradeUuid = "550e8400-e29b-41d4-a716-446655440000"
                iconFile.set(file("icons/icon.ico"))
            }
        }
    }
}
```

- [ ] **步骤 2：准备应用图标**

```bash
mkdir -p desktopApp/icons
# 将 Logo.png 转换为 icon.icns (macOS) 和 icon.ico (Windows)
# 可以使用 `sips` (macOS) 或在线工具
```

---

### 任务 7.4：最终验证和清理

- [ ] **步骤 1：Android 端完整回归测试**

```bash
./gradlew :androidApp:assembleDebug
```

手动测试全部功能：
- 书架浏览、打开书籍、阅读器
- 书签、高亮、笔记
- 文件夹管理
- 设置修改
- Google Drive 同步（登录 → 同步 → 验证数据）

- [ ] **步骤 2：Desktop 端完整测试**

```bash
./gradlew :desktopApp:run
```

手动测试：
- 窗口显示、调整大小
- 导航流畅性
- 拖放导入书籍
- 系统托盘最小化/恢复
- Google Drive OAuth 登录 + 同步

- [ ] **步骤 3：构建 DMG**

```bash
./gradlew :desktopApp:packageDmg
```

验证 DMG 生成在 `desktopApp/build/compose/binaries/main/dmg/` 目录。

- [ ] **步骤 4：删除旧的 app 模块**

确认 androidApp 和 desktopApp 都工作正常后：

```bash
# 更新 settings.gradle.kts 移除 :app
# 删除 app/ 目录
rm -rf app/
```

- [ ] **步骤 5：Final commit**

```bash
git add -A
git commit -m "feat(kmp): complete desktop migration — tray, drag-drop, DMG packaging"
```

---

## 自检

### 1. 规格覆盖度

| 规格章节 | 对应任务 |
|---------|---------|
| 2. 项目结构 | Phase 1 全部 |
| 3. 数据层迁移 | Phase 2 (2.1-2.8) |
| 4. Google Drive 同步 | Phase 6 (6.1-6.4) |
| 5. DI 迁移 | Phase 3 (3.1-3.4) |
| 6. UI 层 | Phase 4 (4.1-4.7) |
| 7.1 窗口配置 | 任务 5.1 |
| 7.2 拖放操作 | 任务 7.1 |
| 7.3 系统托盘 | 任务 7.2 |
| 8. 依赖升级 | Phase 1 build.gradle.kts |
| 10. 实施阶段 | Phase 1-7 对应 |

### 2. 占位符扫描

- `YOUR_CLIENT_ID` — 需要 Google Cloud Console 创建 Desktop OAuth 客户端后替换
- `MIGRATION_2_3` 的 SQL 需从原文件完整复制（已标注）
- 无 TODO/TBD

### 3. 类型一致性

- `DriveCredential` 在 commonMain 定义，androidMain/desktopMain 分别实现 → 一致
- `SyncPreferences` expect/actual 三端签名一致
- `AuthProvider` expect/actual 签名一致
- ViewModel 构造函数参数不变，只改注解 → 一致
