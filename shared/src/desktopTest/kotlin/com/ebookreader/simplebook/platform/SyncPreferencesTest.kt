package com.ebookreader.simplebook.platform

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncPreferencesTest {

    private val tempDir = Files.createTempDirectory("syncprefs-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newPrefs() = SyncPreferences(java.io.File(tempDir, "sync_prefs.properties"))

    @Test
    fun getBoolean_returnsDefault_whenKeyAbsent() {
        val prefs = newPrefs()
        // 键不存在 → 返回传入的 default
        assertFalse(prefs.getBoolean("auto_sync", false))
        assertTrue(prefs.getBoolean("auto_sync", true))
    }

    @Test
    fun putBoolean_roundTripsTrueAndFalse() {
        val prefs = newPrefs()
        prefs.putBoolean("auto_sync", true)
        assertTrue(prefs.getBoolean("auto_sync", false))
        prefs.putBoolean("auto_sync", false)
        assertFalse(prefs.getBoolean("auto_sync", true))
    }

    @Test
    fun putBoolean_persistsAcrossInstances_onSameFile() {
        // 证明 putBoolean 真的落盘并能被新实例重新加载（save + load 往返）
        val file = java.io.File(tempDir, "sync_prefs.properties")
        SyncPreferences(file).putBoolean("auto_sync", true)
        val reloaded = SyncPreferences(file)
        assertTrue(reloaded.getBoolean("auto_sync", false))
    }
}
