package com.ebookreader.simplebook.platform

/**
 * 「需要重新授权」信号。SyncService 在 catch 到可恢复授权异常时发出，UI 消费。
 * 携带原始 [cause]（Throwable，common 类型）：
 * - 桌面：cause 是 [AuthExpiredException]，UI 直接重走 PKCE 登录（不读 cause）。
 * - Android：cause 是 UserRecoverableAuthIOException，UI 从 cause 窄化取 Intent 启动。
 *
 * 注意：expect 主构造只能用 common 类型（不能引用 android.content.Intent），故用 Throwable 承载。
 */
expect class ReauthRequest(cause: Throwable)

/** 若异常表示「可恢复的授权缺失」（桌面=AuthExpiredException / Android=UserRecoverableAuthIOException），
 *  返回包装该异常的 [ReauthRequest]；否则 null。 */
expect fun Throwable.toReauthRequest(): ReauthRequest?
