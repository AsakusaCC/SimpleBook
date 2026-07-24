package com.ebookreader.simplebook.platform

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CompletableFuture

class DesktopOAuthServer(private val port: Int) {
    private var server: HttpServer? = null

    fun start(expectedState: String): CompletableFuture<String> {
        val codeFuture = CompletableFuture<String>()
        val httpServer = HttpServer.create(InetSocketAddress(port), 0)

        httpServer.createContext("/") { exchange: HttpExchange ->
            exchange.use {
                val code = parseAuthorizationCode(it.requestURI.query)
                val state = parseState(it.requestURI.query)

                val response = if (code != null && state == expectedState) {
                    codeFuture.complete(code)
                    SUCCESS_HTML
                } else {
                    codeFuture.completeExceptionally(Exception("OAuth state mismatch or no code"))
                    FAILURE_HTML
                }

                val bytes = response.toByteArray(Charsets.UTF_8)
                it.responseHeaders.set("Content-Type", "text/html; charset=UTF-8")
                it.sendResponseHeaders(200, bytes.size.toLong())
                it.responseBody.use { out -> out.write(bytes) }
            }
        }

        httpServer.executor = null
        httpServer.start()
        server = httpServer
        return codeFuture
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private companion object {
        private const val SUCCESS_HTML =
            "<html><body><h2>Authorization successful!</h2><p>You can close this tab.</p></body></html>"
        private const val FAILURE_HTML =
            "<html><body><h2>Authorization failed.</h2></body></html>"
    }
}

/**
 * 从 OAuth 回调 URL 的 query string 中解析 authorization code。
 * 抽成顶层纯函数便于单元测试。
 *
 * 接受形如 `code=...&state=...` 的 query；返回 code 值或 null。
 */
internal fun parseAuthorizationCode(query: String?): String? {
    if (query.isNullOrEmpty()) return null
    return query.split('&')
        .mapNotNull { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == "code") parts[1] else null
        }
        .firstOrNull()
}

/**
 * 从 OAuth 回调 URL 的 query string 中解析 state 参数。
 * 抽成顶层纯函数便于单元测试。
 *
 * 接受形如 `code=...&state=...` 的 query；返回 state 值或 null。
 * 用于与本地生成的 state 比对，防御 OAuth 登录 CSRF / 登录 fixation。
 */
internal fun parseState(query: String?): String? {
    if (query.isNullOrEmpty()) return null
    return query.split('&')
        .mapNotNull { param ->
            val parts = param.split('=', limit = 2)
            if (parts.size == 2 && parts[0] == "state") parts[1] else null
        }
        .firstOrNull()
}
