plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

import java.util.Properties

kotlin {
    androidTarget()
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            // Compose Multiplatform — exposed via `api` so androidApp/desktopApp consumers
            // (which compose UI directly on top of shared) inherit matching CMP versions.
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.ui)
            implementation(compose.materialIconsExtended)
            implementation(compose.components.resources)

            // Navigation (CMP) — consumed directly by androidApp MainActivity
            api("org.jetbrains.androidx.navigation:navigation-compose:2.9.0-rc02")

            // Lifecycle ViewModel (CMP) — LocalLifecycleOwner used by androidApp
            api("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

            // Lifecycle runtime compose (CMP) — LocalLifecycleOwner in commonMain (App.kt)
            api("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

            // Material3 Window Size Class (CMP) — calculateWindowSizeClass used by androidApp
            api("org.jetbrains.compose.material3:material3-window-size-class:1.9.0")

            // Room KMP
            implementation("androidx.room:room-runtime:2.7.0")

            // Koin
            implementation("io.insert-koin:koin-core:4.0.4")
            implementation("io.insert-koin:koin-compose:4.0.4")
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

            // HTML parser — for EpubReaderView chapter rendering. jsoup (mature Java
            // parser) chosen over Ksoup: Ksoup 0.2.6 is compiled with Kotlin 2.3 but
            // this project is on Kotlin 2.1 (binary metadata mismatch). jsoup is pure
            // Java, works in JVM commonMain like epublib/gson above; API == Ksoup.
            implementation("org.jsoup:jsoup:1.18.3")

            // Encoding detection
            implementation("com.github.albfernandez:juniversalchardet:2.4.0")

            // Coil (CMP)
            implementation("io.coil-kt.coil3:coil-compose:3.0.4")

            // Coil SVG decoder — 解码 SVG（Android: androidsvg；Desktop JVM: Skiko）
            implementation("io.coil-kt.coil3:coil-svg:3.0.4")

            // Koin compose viewmodel — koinViewModel() used directly by androidApp MainActivity
            api("io.insert-koin:koin-compose-viewmodel:4.0.4")
        }

        androidMain.dependencies {
            // Android-specific Room driver
            implementation("androidx.sqlite:sqlite-framework:2.5.0")
            implementation("androidx.room:room-ktx:2.7.0")

            // Android Google Sign-In
            implementation("com.google.android.gms:play-services-auth:21.3.0")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")

            // Android DataStore
            implementation("androidx.datastore:datastore-preferences:1.1.0")

            // Android Core
            implementation("androidx.core:core-ktx:1.13.0")
            implementation("androidx.activity:activity-compose:1.9.0")

            // Koin Android (androidContext / KoinAndroid extension)
            implementation("io.insert-koin:koin-android:4.0.4")

            // Google API Client for Android (GoogleAccountCredential)
            implementation("com.google.api-client:google-api-client-android:2.7.2") {
                exclude(group = "org.apache.httpcomponents")
            }

            // SLF4J Android
            implementation("org.slf4j:slf4j-android:1.7.25")
        }

        val desktopMain by getting {
            kotlin.srcDir("build/generated/oauth/kotlin")
            dependencies {
                // Desktop Room driver (JDBC SQLite)
                implementation("org.xerial:sqlite-jdbc:3.47.2.0")
                implementation("androidx.sqlite:sqlite-bundled:2.5.0")

                // Coroutines Main dispatcher for desktop (viewModelScope uses Dispatchers.Main;
                // core alone doesn't provide it on JVM — Android gets it via kotlinx-coroutines-android)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

                // SLF4J Simple for desktop
                implementation("org.slf4j:slf4j-simple:1.7.36")

                // Compose Desktop specific
                implementation(compose.desktop.currentOs)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// Generate the Google OAuth client secret into a gitignored source file from local.properties.
// The secret must NOT live in the repo (GitHub push protection rejects commits containing it);
// this keeps it out of git while still bundling it into the desktop build (run + package).
val generateOAuthSecret by tasks.registering {
    val outFile = file("build/generated/oauth/kotlin/com/ebookreader/simplebook/platform/OAuthSecrets.kt")
    val localProps = rootProject.file("local.properties")
    inputs.file(localProps).optional()
    outputs.file(outFile)
    doLast {
        val props = Properties()
        localProps.takeIf { it.exists() }?.inputStream()?.use { props.load(it) }
        val secret = props.getProperty("OAUTH_CLIENT_SECRET")
            ?: error("OAUTH_CLIENT_SECRET not set in local.properties — required for desktop Google OAuth")
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            |package com.ebookreader.simplebook.platform
            |
            |/** 生成自 local.properties（OAUTH_CLIENT_SECRET），勿提交。 */
            |internal object OAuthSecrets {
            |    const val CLIENT_SECRET = "${secret.replace("\\", "\\\\").replace("\"", "\\\"")}"
            |}
            """.trimMargin() + "\n"
        )
    }
}

tasks.matching {
    val n = it.name
    (n.startsWith("compile") || n.startsWith("ksp")) && n.contains("Desktop")
}.configureEach { dependsOn(generateOAuthSecret) }

android {
    namespace = "com.ebookreader.simplebook.shared"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Room KSP compiler — generates SimpleBookDatabase_Impl per target.
    // Without this, Room.databaseBuilder().build() throws at runtime:
    // "Cannot find implementation ... _Impl does not exist".
    add("kspAndroid", "androidx.room:room-compiler:2.7.0")
    add("kspDesktop", "androidx.room:room-compiler:2.7.0")
}
