plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Source lives under src/jvmMain/kotlin — register it as the main source set
// so the plain Kotlin JVM plugin picks it up.
sourceSets {
    main {
        kotlin.setSrcDirs(listOf("src/jvmMain/kotlin"))
    }
}

compose.desktop {
    application {
        mainClass = "com.ebookreader.simplebook.MainKt"
        nativeDistributions {
            targetFormats(org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg)
            packageName = "SimpleBook"
            packageVersion = "1.0.0"
            description = "A cross-platform ebook reader"
            vendor = "SimpleBook"
            macOS {
                bundleID = "com.simplebook.desktop"
                minimumSystemVersion = "12.0"
                iconFile.set(file("icons/icon.icns"))
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
