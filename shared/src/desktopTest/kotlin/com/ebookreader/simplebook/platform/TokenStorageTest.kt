package com.ebookreader.simplebook.platform

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenStorageTest {

    private val tempDir: java.io.File = Files.createTempDirectory("tokenstorage-test").toFile()

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private fun newStorage(store: SecretStore = InMemorySecretStore()): TokenStorage =
        TokenStorage(store, java.io.File(tempDir, "nonexistent-legacy"))  // 无旧文件

    @Test
    fun emptyStorage_returnsNulls() {
        val storage = newStorage()
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getExpiresAt())
        assertNull(storage.getUserEmail())
    }

    @Test
    fun saveAndReadTokens_roundTripsAllFields() {
        val storage = newStorage()
        storage.saveTokens("access-123", "refresh-456", 1_700_000_000_000L, "user@example.com")
        assertEquals("access-123", storage.getAccessToken())
        assertEquals("refresh-456", storage.getRefreshToken())
        assertEquals(1_700_000_000_000L, storage.getExpiresAt())
        assertEquals("user@example.com", storage.getUserEmail())
    }

    @Test
    fun saveTokens_persistsAcrossInstances_sharingSameStore() {
        val store = InMemorySecretStore()
        TokenStorage(store, java.io.File(tempDir, "none")).saveTokens("a", "b", 123L, "c@example.com")
        val reloaded = TokenStorage(store, java.io.File(tempDir, "none"))
        assertEquals("a", reloaded.getAccessToken())
        assertEquals("b", reloaded.getRefreshToken())
        assertEquals(123L, reloaded.getExpiresAt())
        assertEquals("c@example.com", reloaded.getUserEmail())
    }

    @Test
    fun saveTokens_handlesNullOptionalFields() {
        val storage = newStorage()
        storage.saveTokens("only-access", null, null, null)
        assertEquals("only-access", storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getExpiresAt())
        assertNull(storage.getUserEmail())
    }

    @Test
    fun clear_removesAllFields() {
        val store = InMemorySecretStore()
        val storage = TokenStorage(store, java.io.File(tempDir, "none"))
        storage.saveTokens("a", "b", 1L, "c@example.com")
        storage.clear()
        assertNull(storage.getAccessToken())
        assertNull(storage.getRefreshToken())
        assertNull(storage.getExpiresAt())
        assertNull(TokenStorage(store, java.io.File(tempDir, "none")).getAccessToken())
    }

    @Test
    fun migrateIfNeeded_importsLegacyFile_andDeletesIt() {
        val store = InMemorySecretStore()
        val legacy = java.io.File(tempDir, "tokens.properties")
        java.io.PrintWriter(legacy).use { w ->
            w.println("access_token=migrated-access")
            w.println("refresh_token=migrated-refresh")
            w.println("user_email=migrated@example.com")
        }
        assertTrue(legacy.exists())

        val storage = TokenStorage(store, legacy)

        assertEquals("migrated-access", storage.getAccessToken())
        assertEquals("migrated-refresh", storage.getRefreshToken())
        assertEquals("migrated@example.com", storage.getUserEmail())
        assertNull(storage.getExpiresAt())
        assertFalse(legacy.exists(), "迁移后旧文件应被删除")
    }

    @Test
    fun migrateIfNeeded_skipsWhenKeychainAlreadyHasData() {
        val store = InMemorySecretStore()
        store.write("tokens", "existing-blob")
        val legacy = java.io.File(tempDir, "tokens.properties")
        java.io.PrintWriter(legacy).use { it.println("access_token=should-not-be-used") }

        val storage = TokenStorage(store, legacy)

        assertNull(storage.getAccessToken())
        assertTrue(legacy.exists(), "已迁移过则不动旧文件")
    }

    @Test
    fun migrateIfNeeded_unreadableLegacyFile_skipsMigration() {
        val store = InMemorySecretStore()
        val legacy = java.io.File(tempDir, "tokens.properties")
        java.io.PrintWriter(legacy).use { it.println("access_token=should-not-migrate") }
        assertTrue(legacy.exists())
        // 去掉读权限，使 inputStream() 抛异常 → migrateIfNeeded 的 catch 命中
        assertTrue(legacy.setReadable(false), "应能移除读权限")

        val storage = TokenStorage(store, legacy)

        assertNull(storage.getAccessToken(), "不可读旧文件应跳过迁移")
        assertNull(store.read("tokens"), "Keychain 不应有数据")
        assertTrue(legacy.exists(), "跳过迁移则旧文件仍在")

        // 恢复可读，避免 tearDown 的 deleteRecursively() 因权限不足删不掉
        legacy.setReadable(true)
    }
}
