package com.ebookreader.simplebook.domain.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ebookreader.simplebook.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

class SyncForegroundService : Service() {

    private val syncService: SyncService by lazy {
        KoinPlatform.getKoin().get<SyncService>()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "SyncForegroundSvc"
        private const val CHANNEL_ID = "sync_channel"
        private const val NOTIFICATION_ID = 1

        fun start(context: Context) {
            Log.d(TAG, "start: requesting start")
            val intent = Intent(context, SyncForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            Log.d(TAG, "stop: requesting stop")
            context.stopService(Intent(context, SyncForegroundService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在同步…"))
        Log.d(TAG, "onCreate: startForeground called")

        // If sync already completed before we started, stop immediately
        if (syncService.syncStatus.value !is SyncStatus.Syncing) {
            Log.d(TAG, "onCreate: sync not in progress (status=${syncService.syncStatus.value}), stopping")
            stopSelf()
            return
        }

        serviceScope.launch {
            syncService.syncStatus.collect { status ->
                Log.d(TAG, "syncStatus changed: $status")
                when (status) {
                    is SyncStatus.Success -> {
                        updateNotification("同步完成")
                        kotlinx.coroutines.delay(2000)
                        stopSelf()
                    }
                    is SyncStatus.Error -> {
                        updateNotification("同步失败：${status.message}")
                        kotlinx.coroutines.delay(4000)
                        stopSelf()
                    }
                    else -> {}
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_sync_notification)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
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
