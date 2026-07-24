package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ebookreader.simplebook.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    fun getBookmarksForBook(bookUuid: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("UPDATE bookmarks SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid AND isDeleted = 0 ORDER BY createdAt DESC")
    suspend fun getBookmarksForBookNow(bookUuid: String): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE bookUuid = :bookUuid ORDER BY createdAt DESC")
    suspend fun getAllBookmarksForBookNow(bookUuid: String): List<BookmarkEntity>

    @Query("DELETE FROM bookmarks WHERE bookUuid = :bookUuid")
    suspend fun hardDeleteByBook(bookUuid: String)
}