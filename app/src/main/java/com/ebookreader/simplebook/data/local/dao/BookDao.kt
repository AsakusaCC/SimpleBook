package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.ebookreader.simplebook.data.local.entity.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Insert
    suspend fun insert(book: BookEntity): Long

    @Update
    suspend fun update(book: BookEntity)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("SELECT * FROM books ORDER BY lastReadAt DESC NULLS LAST, addedAt DESC")
    suspend fun getAllBooksNow(): List<BookEntity>

    @Query("SELECT * FROM books WHERE driveFileId = :driveFileId")
    suspend fun getBookByDriveFileId(driveFileId: String): BookEntity?
}
