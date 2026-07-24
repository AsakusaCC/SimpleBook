package com.ebookreader.simplebook.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations — Android only.
 * Desktop creates the database with the latest schema directly.
 *
 * SQL 集中存放在 commonMain 的 [MigrationSql]（与 desktopTest 冒烟测试共用同一份），
 * 这里仅做 Room [Migration] 包装。
 */
object DatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.MIGRATION_1_2.forEach { db.execSQL(it) }
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.MIGRATION_2_3.forEach { db.execSQL(it) }
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            MigrationSql.MIGRATION_3_4.forEach { db.execSQL(it) }
        }
    }
}
