package com.ebookreader.simplebook.platform

import com.google.gson.Gson
import java.io.File
import java.util.Base64
import java.util.Properties

/**
 * Token 持久化。后端为 [SecretStore]（生产=macOS Keychain）。
 * 单 account 存一个 Base64(JSON) blob，包含 access/refresh/expiresAt/email。
 * AuthProvider 在内存缓存 token，本类仅在 登录/刷新/登出/启动 读写。
 */
class TokenStorage(
    private val secretStore: SecretStore = SecurityCliStore(),
    private val legacyFile: File = File(
        System.getProperty("user.home"), "Library/SimpleBook/tokens.properties"
    )
) {
    private val gson = Gson()

    init { migrateIfNeeded() }

    fun getAccessToken(): String? = loadBlob().accessToken
    fun getRefreshToken(): String? = loadBlob().refreshToken
    fun getExpiresAt(): Long? = loadBlob().expiresAt
    fun getUserEmail(): String? = loadBlob().email

    fun saveTokens(accessToken: String, refreshToken: String?, expiresAt: Long?, email: String?) {
        val blob = TokenBlob(accessToken, refreshToken, expiresAt, email)
        secretStore.write(ACCOUNT, encode(blob))
    }

    fun clear() {
        secretStore.delete(ACCOUNT)
    }

    /** 一次性迁移：Keychain 空 + 旧明文文件存在 → 导入后删文件。 */
    private fun migrateIfNeeded() {
        if (secretStore.read(ACCOUNT) != null) return      // 已在 Keychain
        if (!legacyFile.exists()) return
        val props = Properties()
        try {
            legacyFile.inputStream().use { props.load(it) }
        } catch (e: Exception) {
            return   // 旧文件不可读 → 跳过
        }
        val access = props.getProperty("access_token") ?: return
        saveTokens(
            accessToken = access,
            refreshToken = props.getProperty("refresh_token"),
            expiresAt = props.getProperty("expires_at")?.toLongOrNull(),
            email = props.getProperty("user_email")
        )
        // 删除失败 → 明文 token 文件残留（安全瑕疵），留痕便于排查
        if (!legacyFile.delete() && legacyFile.exists()) {
            logW("TokenStorage", "failed to delete legacy token file: ${legacyFile.absolutePath}")
        }
    }

    private fun loadBlob(): TokenBlob {
        val raw = secretStore.read(ACCOUNT) ?: return TokenBlob()
        return try {
            gson.fromJson(String(Base64.getDecoder().decode(raw), Charsets.UTF_8), TokenBlob::class.java)
        } catch (e: Exception) {
            TokenBlob()   // 损坏 → 视为空
        }
    }

    private fun encode(blob: TokenBlob): String =
        Base64.getEncoder().encodeToString(gson.toJson(blob).toByteArray(Charsets.UTF_8))

    private data class TokenBlob(
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresAt: Long? = null,
        val email: String? = null
    )

    companion object {
        private const val ACCOUNT = "tokens"
    }
}
