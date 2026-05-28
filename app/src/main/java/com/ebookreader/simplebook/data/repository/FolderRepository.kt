package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun getAllFolders(): Flow<List<Folder>>
    suspend fun getFolderByUuid(uuid: String): Folder?
    suspend fun getFolderByDriveFileId(driveFileId: String): Folder?
    suspend fun addFolder(folder: Folder): String
    suspend fun updateFolder(folder: Folder)
    suspend fun softDeleteFolder(uuid: String)
    suspend fun getBookCountInFolder(folderId: String): Int
    suspend fun getAllFoldersIncludingDeleted(): List<Folder>
}
