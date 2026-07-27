package com.ebookreader.simplebook.platform

expect class SyncPreferences() {
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getStringSet(key: String, default: Set<String>? = null): Set<String>?
    fun putStringSet(key: String, value: Set<String>)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
}
