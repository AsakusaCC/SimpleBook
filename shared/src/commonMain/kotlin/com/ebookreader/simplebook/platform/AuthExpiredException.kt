package com.ebookreader.simplebook.platform

/**
 * 桌面 access_token 刷新失败（refresh_token 缺失 / invalid_grant）时抛出。
 * 经 Drive.execute() 传到 SyncService catch，由 [toReauthRequest] 映射为 reauth 信号。
 */
class AuthExpiredException(
    message: String = "Access token refresh failed; re-authentication required"
) : Exception(message)
