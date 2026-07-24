package com.ebookreader.simplebook.platform

/**
 * Platform-agnostic handle to the OS foreground sync notification service.
 *
 * - Android: starts/stops [com.ebookreader.simplebook.domain.service.SyncForegroundService].
 * - Desktop: no-op (desktop has no foreground service concept).
 *
 * Created with a no-arg constructor so it can be registered in Koin via
 * `singleOf(::ForegroundSyncController)` on each platform.
 */
expect class ForegroundSyncController() {
    fun start()
    fun stop()
}
