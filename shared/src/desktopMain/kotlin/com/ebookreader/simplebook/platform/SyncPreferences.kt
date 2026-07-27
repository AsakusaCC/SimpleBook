package com.ebookreader.simplebook.platform

import java.io.File
import java.util.Properties

actual class SyncPreferences {
    private val file: File
    private val props: Properties

    actual constructor() : this(
        File(System.getProperty("user.home"), "Library/SimpleBook/sync_prefs.properties")
    )

    // 测试缝隙：注入任意文件，避免污染真实 home 目录（同 TokenStorage 的可测性模式）
    internal constructor(file: File) {
        this.file = file
        this.props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    }

    actual fun getLong(key: String, default: Long): Long =
        props.getProperty(key)?.toLongOrNull() ?: default

    actual fun putLong(key: String, value: Long) {
        props[key] = value.toString()
        save()
    }

    actual fun getStringSet(key: String, default: Set<String>?): Set<String>? =
        props.getProperty(key)?.split(",")?.toSet() ?: default

    actual fun putStringSet(key: String, value: Set<String>) {
        props[key] = value.joinToString(",")
        save()
    }

    actual fun getBoolean(key: String, default: Boolean): Boolean =
        props.getProperty(key)?.toBoolean() ?: default

    actual fun putBoolean(key: String, value: Boolean) {
        props[key] = value.toString()
        save()
    }

    private fun save() {
        file.parentFile.mkdirs()
        // Non-sensitive data (sync timestamps, import-id cache) — no owner-only
        // hardening here, unlike TokenStorage which holds OAuth tokens.
        file.outputStream().use { props.store(it, null) }
    }
}
