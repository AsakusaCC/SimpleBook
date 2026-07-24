package com.ebookreader.simplebook.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File
import org.koin.mp.KoinPlatform

private const val DB_NAME = "simplebook.db"
private const val TAG = "DatabaseBuilder"

actual fun getRoomDatabaseBuilder(): RoomDatabase.Builder<SimpleBookDatabase> {
    val context = KoinPlatform.getKoin().get<Context>()
    // 在 Room 打开数据库（并触发迁移）之前做一次性快照，防迁移失败/被清空时丢数据。
    backupBeforeMigration(context)
    return Room.databaseBuilder(
        context,
        SimpleBookDatabase::class.java,
        DB_NAME
    )
}

/**
 * 在 Room 打开数据库（触发迁移）之前，对现有库文件做**一次性**快照备份。
 *
 * 触发条件：[DB_NAME] 存在、且 `simplebook.db.bak` 尚不存在（只备份一次，保留最早的
 * 升级前状态）。迁移失败或被 `fallbackToDestructiveMigration` 清空时，用户可从 `.bak`
 * 手动恢复（adb root 后 pull，或后续接入 app 内还原）。
 *
 * 备份失败仅记日志、不阻塞 app 启动——迁移本身才是主路径。
 */
private fun backupBeforeMigration(context: Context) {
    val dbFile = context.getDatabasePath(DB_NAME)
    if (!dbFile.exists()) return
    val dir = dbFile.parentFile ?: return
    val bak = File(dir, "$DB_NAME.bak")
    if (bak.exists()) return // 一次性快照，已备份则保留最早版本
    try {
        dbFile.copyTo(bak, overwrite = false)
        // WAL 模式下的伴生文件（可能含未 checkpoint 的数据）
        listOf("$DB_NAME-wal", "$DB_NAME-shm").forEach { name ->
            val src = File(dir, name)
            if (src.exists()) {
                src.copyTo(File(dir, "$name.bak"), overwrite = false)
            }
        }
        Log.i(TAG, "pre-migration backup created: ${bak.absolutePath}")
    } catch (e: Exception) {
        Log.w(TAG, "pre-migration backup failed (non-fatal)", e)
    }
}
