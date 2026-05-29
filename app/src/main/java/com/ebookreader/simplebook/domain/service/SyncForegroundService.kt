package com.ebookreader.simplebook.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.ebookreader.simplebook.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SyncForegroundService : Service() {

    @Inject lateinit var syncService: SyncService

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            val intent = Intent(context, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在同步…"))

        serviceScope.launch {
            var seenSyncing = false
            syncService.syncStatus.collect { status ->
                if (status is SyncStatus.Syncing) seenSyncing = true
                if (seenSyncing && status !is SyncStatus.Syncing) {
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "同步",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示书籍同步进度"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
