package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.FolderRepository
import com.ebookreader.simplebook.domain.model.Folder
import kotlinx.coroutines.flow.Flow

class FolderService constructor(
    private val folderRepository: FolderRepository
) {
    fun getAllFolders(): Flow<List<Folder>> = folderRepository.getAllFolders()

    suspend fun createFolder(name: String): Folder {
        val folder = Folder(name = name)
        folderRepository.addFolder(folder)
        return folder
    }

    suspend fun softDeleteFolder(uuid: String) = folderRepository.softDeleteFolder(uuid)

    suspend fun getBookCountInFolder(folderId: String): Int =
        folderRepository.getBookCountInFolder(folderId)

    suspend fun getFolderByUuid(uuid: String): Folder? =
        folderRepository.getFolderByUuid(uuid)
}
