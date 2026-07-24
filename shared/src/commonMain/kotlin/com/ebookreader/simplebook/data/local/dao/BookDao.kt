package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebookreader.simplebook.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE uuid = :uuid")
    suspend fun getBookByUuid(uuid: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: BookEntity)

    @Update
    suspend fun update(book: BookEntity)

    @Query("UPDATE books SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    suspend fun getAllBooksNow(): List<BookEntity>

    @Query("SELECT * FROM books WHERE isDeleted = 1")
    suspend fun getDeletedBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE driveFileId = :driveFileId")
    suspend fun getBookByDriveFileId(driveFileId: String): BookEntity?

    @Query("SELECT * FROM books")
    suspend fun getAllBooksIncludingDeleted(): List<BookEntity>

    @Query("SELECT * FROM books WHERE folderId IS NULL AND isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    fun getShelfBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE folderId = :folderId AND isDeleted = 0 ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    fun getBooksInFolder(folderId: String): Flow<List<BookEntity>>

    @Query("""
        SELECT DISTINCT b.* FROM books b
        WHERE b.lastSyncedAt IS NULL
           OR b.driveFileId IS NULL
           OR b.updatedAt > b.lastSyncedAt
           OR EXISTS (SELECT 1 FROM reading_progress rp WHERE rp.bookUuid = b.uuid AND rp.updatedAt > b.lastSyncedAt)
           OR EXISTS (SELECT 1 FROM bookmarks bm WHERE bm.bookUuid = b.uuid AND bm.updatedAt > b.lastSyncedAt)
           OR EXISTS (SELECT 1 FROM highlights hl WHERE hl.bookUuid = b.uuid AND hl.updatedAt > b.lastSyncedAt)
           OR EXISTS (SELECT 1 FROM notes nt WHERE nt.bookUuid = b.uuid AND nt.updatedAt > b.lastSyncedAt)
        """)
    suspend fun getDirtyBooks(): List<BookEntity>

    @Query("DELETE FROM books WHERE uuid = :uuid")
    suspend fun hardDelete(uuid: String)
}