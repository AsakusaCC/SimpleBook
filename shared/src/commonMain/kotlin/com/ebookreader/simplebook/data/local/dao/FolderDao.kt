package com.ebookreader.simplebook.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ebookreader.simplebook.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE uuid = :uuid")
    suspend fun getFolderByUuid(uuid: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity)

    @Update
    suspend fun update(folder: FolderEntity)

    @Query("UPDATE folders SET isDeleted = 1, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM books WHERE folderId = :folderId AND isDeleted = 0")
    suspend fun getBookCountInFolder(folderId: String): Int

    @Query("SELECT * FROM folders WHERE isDeleted = 1")
    suspend fun getDeletedFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersIncludingDeleted(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE driveFileId = :driveFileId")
    suspend fun getFolderByDriveFileId(driveFileId: String): FolderEntity?
}
