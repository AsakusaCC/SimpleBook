package com.ebookreader.simplebook.platform

/**
 * Desktop has no foreground-service concept; sync runs as a plain coroutine
 * launched from [com.ebookreader.simplebook.ui.sync.SyncViewModel].
 */
actual class ForegroundSyncController actual constructor() {
    actual fun start() {}
    actual fun stop() {}
}
