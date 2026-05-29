package com.ebookreader.simplebook.data.remote

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.FileContent
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.FileList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import com.ebookreader.simplebook.domain.model.FolderInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authManager: AuthManager
) {
    private val drive: Drive?
        get() = authManager.signedInAccount.value?.let { account ->
            val credential = GoogleAccountCredential.usingOAuth2(
                context, listOf(DriveScopes.DRIVE_APPDATA)
            )
            credential.selectedAccount = account.account
            Drive.Builder(
                com.google.api.client.http.javanet.NetHttpTransport(),
                GsonFactory.getDefaultInstance()
            ) { request ->
                credential.initialize(request)
                request.readTimeout = 300000
                request.connectTimeout = 15000
            }.setApplicationName("SimpleBook").build()
        }

    suspend fun uploadFile(
        folderId: String,
        fileName: String,
        content: ByteArray,
        mimeType: String = "application/octet-stream"
    ): String? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val existing = findFileInFolder(folderId, fileName)
        if (existing != null) {
            drive.files().update(existing, com.google.api.services.drive.model.File().apply {
                name = fileName
            }, ByteArrayContent(mimeType, content)).execute().id
        } else {
            drive.files().create(
                com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf(folderId)
                },
                ByteArrayContent(mimeType, content)
            ).setFields("id").execute().id
        }
    }

    suspend fun uploadBookFile(
        folderId: String,
        fileName: String,
        localFile: File,
        mimeType: String
    ): String? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val existing = findFileInFolder(folderId, fileName)
        if (existing != null) {
            drive.files().update(
                existing,
                com.google.api.services.drive.model.File(),
                FileContent(mimeType, localFile)
            ).execute().id
        } else {
            drive.files().create(
                com.google.api.services.drive.model.File().apply {
                    name = fileName
                    parents = listOf(folderId)
                },
                FileContent(mimeType, localFile)
            ).setFields("id").execute().id
        }
    }

    suspend fun downloadFile(fileId: String): ByteArray? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val out = ByteArrayOutputStream()
        drive.files().get(fileId).executeMediaAndDownloadTo(out)
        out.toByteArray()
    }

    suspend fun downloadFileTo(fileId: String, targetFile: File) = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext
        targetFile.outputStream().use { out ->
            drive.files().get(fileId).executeMediaAndDownloadTo(out)
        }
    }

    suspend fun createFolder(name: String, parentId: String): String? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val existing = findFileInFolder(parentId, name)
        if (existing != null) return@withContext existing
        drive.files().create(
            com.google.api.services.drive.model.File().apply {
                this.name = name
                mimeType = "application/vnd.google-apps.folder"
                parents = listOf(parentId)
            }
        ).setFields("id").execute().id
    }

    suspend fun getAppFolderId(): String = "appDataFolder"

    suspend fun findFileInFolder(folderId: String, fileName: String): String? = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext null
        val query = "'$folderId' in parents and name='$fileName' and trashed=false"
        val result: FileList = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id)")
            .execute()
        result.files.firstOrNull()?.id
    }

    suspend fun listFilesInFolder(folderId: String): List<FolderInfo> = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext emptyList()
        val query = "'$folderId' in parents and trashed=false"
        val result: FileList = drive.files().list()
            .setSpaces("appDataFolder")
            .setQ(query)
            .setFields("files(id, name, modifiedTime)")
            .execute()
        result.files.map { file ->
            FolderInfo(
                name = file.name,
                id = file.id,
                modifiedTime = file.modifiedTime?.toStringRfc3339()
            )
        }
    }

    suspend fun touchFolder(folderId: String) = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext
        drive.files().update(folderId, com.google.api.services.drive.model.File().apply {
            modifiedTime = com.google.api.client.util.DateTime(System.currentTimeMillis())
        }).setFields("id").execute()
    }

    suspend fun deleteFile(fileId: String) = withContext(Dispatchers.IO) {
        val drive = drive ?: return@withContext
        drive.files().delete(fileId).execute()
    }
}
