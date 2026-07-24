plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

import java.util.Properties

// 从 local.properties（gitignored）读签名密码，避免硬编码进仓库
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.ebookreader.simplebook"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ebookreader.simplebook"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.8.7"
    }

    signingConfigs {
        create("release") {
            // jks 在仓库根（gitignored）；main 用 file() 是模块相对路径，这里须 rootProject.file()
            storeFile = rootProject.file("simplebook-release.jks")
            storePassword = localProps.getProperty("SIGNING_STORE_PASSWORD", "")
            keyAlias = "simplebook"
            keyPassword = localProps.getProperty("SIGNING_KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.core:core-ktx:1.13.0")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Koin Android (startKoin + androidContext in SimpleBookApp)
    implementation("io.insert-koin:koin-android:4.0.4")
}
