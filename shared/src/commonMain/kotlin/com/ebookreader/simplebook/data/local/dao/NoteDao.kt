package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getNotesForBook(bookUuid: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM notes WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getNotesForBookNow(bookUuid: String): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE bookUuid = :bookUuid ORDER BY createdAt DESC")
    suspend fun getAllNotesForBookNow(bookUuid: String): List<NoteEntity>

    @Query("DELETE FROM notes WHERE bookUuid = :bookUuid")
    suspend fun hardDeleteByBook(bookUuid: String)
}