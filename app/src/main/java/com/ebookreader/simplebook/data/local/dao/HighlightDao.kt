package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid AND chapterIndex = :chapterIndex AND isDeleted = 0")
    fun getHighlightsForChapter(bookUuid: String, chapterIndex: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getHighlightsForBook(bookUuid: String): Flow<List<HighlightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity)

    @Query("UPDATE highlights SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getHighlightsForBookNow(bookUuid: String): List<HighlightEntity>

    @Query("SELECT * FROM highlights WHERE bookUuid = :bookUuid ORDER BY createdAt DESC")
    suspend fun getAllHighlightsForBookNow(bookUuid: String): List<HighlightEntity>
}