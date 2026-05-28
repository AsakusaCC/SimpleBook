package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.FolderDao
import com.ebookreader.simplebook.data.local.entity.FolderEntity
import com.ebookreader.simplebook.domain.model.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
) : FolderRepository {

    override fun getAllFolders(): Flow<List<Folder>> =
        folderDao.getAllFolders().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getFolderByUuid(uuid: String): Folder? =
        folderDao.getFolderByUuid(uuid)?.toDomain()

    override suspend fun getFolderByDriveFileId(driveFileId: String): Folder? =
        folderDao.getFolderByDriveFileId(driveFileId)?.toDomain()

    override suspend fun addFolder(folder: Folder): String {
        folderDao.insert(folder.toEntity())
        return folder.uuid
    }

    override suspend fun updateFolder(folder: Folder) =
        folderDao.update(folder.toEntity())

    override suspend fun softDeleteFolder(uuid: String) =
        folderDao.softDelete(uuid)

    override suspend fun getBookCountInFolder(folderId: String): Int =
        folderDao.getBookCountInFolder(folderId)

    override suspend fun getAllFoldersIncludingDeleted(): List<Folder> =
        folderDao.getAllFoldersIncludingDeleted().map { it.toDomain() }

    private fun FolderEntity.toDomain() = Folder(
        uuid = uuid, name = name, createdAt = createdAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, driveFileId = driveFileId
    )

    private fun Folder.toEntity() = FolderEntity(
        uuid = uuid, name = name, createdAt = createdAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, driveFileId = driveFileId
    )
}
