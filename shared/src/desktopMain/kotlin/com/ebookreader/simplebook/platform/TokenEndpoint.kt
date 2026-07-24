package com.ebookreader.simplebook.platform

import com.google.gson.JsonParser
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** OAuth token 端点的可注入抽象（生产=HTTP，测试=fake）。 */
internal interface TokenEndpoint {
    /** 用 authorization code 换 token。返回 null 表示 error（如 invalid_grant）；抛 IOException 表示网络失败。 */
    fun exchange(code: String, codeVerifier: String, redirectUri: String): TokenSet?
    /** 用 refresh_token 换新 token。返回 null 表示 invalid_grant；抛 IOException 表示网络失败。 */
    fun refresh(refreshToken: String): TokenSet?
    /** 取 userinfo email；失败返回 null（不阻断登录）。 */
    fun fetchEmail(accessToken: String): String?
}

internal data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?
)

internal object OAuthConfig {
    const val CLIENT_ID = "347561784963-bk68r08k3c33fnbjc36m4haelsnun1td.apps.googleusercontent.com"
    // CLIENT_SECRET 从 local.properties 经 Gradle 生成（OAuthSecrets），不进仓库（GitHub push 保护会拦）
    const val PORT = 8089
    const val SCOPES =
        "https://www.googleapis.com/auth/drive.appdata https://www.googleapis.com/auth/drive"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"
}

internal class HttpTokenEndpoint : TokenEndpoint {

    override fun exchange(code: String, codeVerifier: String, redirectUri: String): TokenSet? {
        val params = buildString {
            append("grant_type=authorization_code")
            append("&code=${URLEncoder.encode(code, "UTF-8")}")
            append("&client_id=${URLEncoder.encode(OAuthConfig.CLIENT_ID, "UTF-8")}")
            append("&client_secret=${URLEncoder.encode(OAuthSecrets.CLIENT_SECRET, "UTF-8")}")
            append("&redirect_uri=${URLEncoder.encode(redirectUri, "UTF-8")}")
            append("&code_verifier=${URLEncoder.encode(codeVerifier, "UTF-8")}")
        }
        val (httpCode, body) = httpPost(OAuthConfig.TOKEN_ENDPOINT, params)
        if (httpCode >= 500) throw IOException("token endpoint HTTP $httpCode")   // 瞬时服务器错误 → 同步失败可重试，不踢重登
        return parseTokenResponse(body)
    }

    override fun refresh(refreshToken: String): TokenSet? {
        val params = buildString {
            append("grant_type=refresh_token")
            append("&refresh_token=${URLEncoder.encode(refreshToken, "UTF-8")}")
            append("&client_id=${URLEncoder.encode(OAuthConfig.CLIENT_ID, "UTF-8")}")
            append("&client_secret=${URLEncoder.encode(OAuthSecrets.CLIENT_SECRET, "UTF-8")}")
        }
        val (httpCode, body) = httpPost(OAuthConfig.TOKEN_ENDPOINT, params)
        if (httpCode >= 500) throw IOException("token endpoint HTTP $httpCode")   // 瞬时服务器错误 → 同步失败可重试，不踢重登
        return parseTokenResponse(body)
    }

    override fun fetchEmail(accessToken: String): String? {
        val conn = (URL(OAuthConfig.USERINFO_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JsonParser.parseString(body).asJsonObject
            return root.get("email")?.takeIf { !it.isJsonNull }?.asString
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTokenResponse(body: String?): TokenSet? {
        if (body.isNullOrEmpty()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            if (root.get("error") != null) return null   // invalid_grant 等
            val access = root.get("access_token")?.takeIf { !it.isJsonNull }?.asString ?: return null
            val refresh = root.get("refresh_token")?.takeIf { !it.isJsonNull }?.asString
            val expiresIn = root.get("expires_in")?.takeIf { !it.isJsonNull }?.asLong
            TokenSet(access, refresh, expiresIn)
        } catch (e: Exception) {
            null   // 损坏 body 不崩（非 JSON / 结构不符）→ 当作无可用 token
        }
    }

    private fun httpPost(url: String, params: String): Pair<Int, String?> {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        try {
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(params) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }
            logD("HttpTokenEndpoint", "$url -> HTTP $code (${body?.length ?: 0} bytes)")
            return code to body
        } finally {
            conn.disconnect()
        }
    }
}
