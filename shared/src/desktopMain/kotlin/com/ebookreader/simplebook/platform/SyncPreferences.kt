package com.ebookreader.simplebook.platform

import java.io.File
import java.util.Properties

actual class SyncPreferences actual constructor() {
    private val file = File(System.getProperty("user.home"), "Library/SimpleBook/sync_prefs.properties")
    private val props = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }

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

    private fun save() {
        file.parentFile.mkdirs()
        // Non-sensitive data (sync timestamps, import-id cache) — no owner-only
        // hardening here, unlike TokenStorage which holds OAuth tokens.
        file.outputStream().use { props.store(it, null) }
    }
}
