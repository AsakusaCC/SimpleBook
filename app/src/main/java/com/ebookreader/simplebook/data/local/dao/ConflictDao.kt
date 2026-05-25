package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.ConflictRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConflictDao {
    @Query("SELECT * FROM conflict_records WHERE resolvedAt IS NULL")
    fun getUnresolvedConflicts(): Flow<List<ConflictRecordEntity>>

    @Query("SELECT * FROM conflict_records WHERE resolvedAt IS NULL")
    suspend fun getUnresolvedConflictsNow(): List<ConflictRecordEntity>

    @Query("SELECT * FROM conflict_records WHERE resolvedAt IS NULL AND bookId = :bookId")
    suspend fun getUnresolvedForBook(bookId: Long): List<ConflictRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(conflict: ConflictRecordEntity)

    @Query("UPDATE conflict_records SET resolvedAt = :resolvedAt WHERE id = :id")
    suspend fun markResolved(id: Long, resolvedAt: Long)

    @Query("DELETE FROM conflict_records WHERE resolvedAt IS NOT NULL")
    suspend fun deleteResolved()
}
