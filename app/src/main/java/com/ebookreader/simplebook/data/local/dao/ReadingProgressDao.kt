package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.ReadingProgressEntity

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE bookUuid = :bookUuid AND isDeleted = 0")
    suspend fun getProgress(bookUuid: String): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: ReadingProgressEntity)

    @Query("SELECT * FROM reading_progress WHERE bookUuid = :bookUuid")
    suspend fun getProgressIncludingDeleted(bookUuid: String): ReadingProgressEntity?
}