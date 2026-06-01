package com.ebookreader.simplebook.domain.service

import android.content.Context
import android.util.Log
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.local.entity.SyncLogEntity
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.data.remote.BookMetadata
import com.ebookreader.simplebook.data.remote.BookmarkMetadata
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.data.remote.HighlightMetadata
import com.ebookreader.simplebook.data.remote.NoteMetadata
import com.ebookreader.simplebook.data.remote.ProgressMetadata
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.data.repository.FolderRepository
import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.data.remote.FolderMetadata
import com.ebookreader.simplebook.data.remote.FolderSyncData
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.FolderInfo
import com.ebookreader.simplebook.domain.model.Highlight
import com.ebookreader.simplebook.domain.model.Folder
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.ReadingProgress
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Error(val message: String) : SyncStatus()
    data object Success : SyncStatus()
}

@Singleton
class SyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val driveClient: GoogleDriveClient,
    private val authManager: AuthManager,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val noteRepository: NoteRepository,
    private val folderRepository: FolderRepository,
    private val syncLogDao: SyncLogDao,
    private val gson: Gson,
    private val epubParser: EpubParser
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val _lastSyncedAt = MutableStateFlow<Long?>(prefs.getLong("last_synced_at", 0L).takeIf { it > 0 })
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val syncMutex = Mutex()

    companion object {
        private const val TAG = "SyncService"
        private const val FOLDERS_FILENAME = "folders.json"
    }

    // ── Public API ──────────────────────────────────────────────────

    suspend fun syncAll() {
        Log.d(TAG, "syncAll: isSignedIn=${authManager.isSignedIn}")
        if (!authManager.isSignedIn) {
            _syncStatus.value = SyncStatus.Error("Not signed in")
            return
        }

        syncMutex.withLock {
            try {
                _syncStatus.value = SyncStatus.Syncing
                Log.d(TAG, "syncAll: starting pullFromRemote")
                pullFromRemote()
                Log.d(TAG, "syncAll: starting pushToRemote")
                pushToRemote()
                Log.d(TAG, "syncAll: starting syncFolders")
                syncFolders()
                val now = System.currentTimeMillis()
                _lastSyncedAt.value = now
                prefs.edit().putLong("last_synced_at", now).apply()
                _syncStatus.value = SyncStatus.Success
                Log.d(TAG, "syncAll: completed successfully")
            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.w(TAG, "syncAll: cancelled", e)
                _syncStatus.value = SyncStatus.Error("同步被中断，请重试")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "syncAll: failed", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "同步失败")
            }
        }
    }

    // ── Push ────────────────────────────────────────────────────────

    suspend fun pushToRemote() {
        val appFolderId = driveClient.getAppFolderId()
        val books = bookRepository.getDirtyBooks()
        Log.d(TAG, "pushToRemote: appFolderId=$appFolderId, dirtyBooks=${books.size}")

        for (book in books) {
            Log.d(TAG, "pushToRemote: pushing book=${book.title}, uuid=${book.uuid}, isDeleted=${book.isDeleted}")
            var currentBook = book

            // ── Deleted book: only update metadata if Drive folder still exists ──
            if (currentBook.isDeleted) {
                val existingFolderId = driveClient.findFileInFolder(appFolderId, "book_${currentBook.uuid}")
                if (existingFolderId != null) {
                    // Folder still on Drive → push metadata to propagate deletion to other devices
                    val progress = readingProgressRepository.getProgressIncludingDeleted(currentBook.uuid)
                    val bookmarks = bookmarkRepository.getAllBookmarksForBookNow(currentBook.uuid)
                    val highlights = highlightRepository.getAllHighlightsForBookNow(currentBook.uuid)
                    val notes = noteRepository.getAllNotesForBookNow(currentBook.uuid)
                    val now = System.currentTimeMillis()
                    val updatedBook = currentBook.copy(updatedAt = now)
                    val metadata = buildBookMetadata(updatedBook, progress, bookmarks, highlights, notes)
                    val metadataJson = gson.toJson(metadata)
                    driveClient.uploadFile(existingFolderId, "metadata.json", metadataJson.toByteArray(), "application/json")
                    driveClient.touchFolder(existingFolderId)
                    bookRepository.updateBook(updatedBook.copy(lastSyncedAt = now))
                    Log.d(TAG, "pushToRemote: updated metadata for deleted book ${currentBook.uuid}")
                } else {
                    // Folder already cleaned from Drive → skip push
                    val now = System.currentTimeMillis()
                    bookRepository.updateBook(currentBook.copy(lastSyncedAt = now))
                    Log.d(TAG, "pushToRemote: skipped deleted book ${currentBook.uuid} (folder already cleaned)")
                }
                continue
            }

            val bookFolderName = "book_${currentBook.uuid}"
            val bookFolderId = driveClient.createFolder(bookFolderName, appFolderId)
                ?: throw Exception("Failed to create book folder for ${currentBook.uuid}")

            // Upload book file on first sync
            if (currentBook.driveFileId == null) {
                val fileName = "${currentBook.title}.${currentBook.format.name.lowercase()}"
                val localFile = File(currentBook.filePath)
                if (localFile.exists()) {
                    Log.d(TAG, "pushToRemote: uploading book file $fileName, size=${localFile.length()} bytes")
                    val mimeType = when (currentBook.format) {
                        BookFormat.EPUB -> "application/epub+zip"
                        BookFormat.TXT -> "text/plain"
                    }
                    val fileId = driveClient.uploadBookFile(bookFolderId, fileName, localFile, mimeType)
                    if (fileId != null) {
                        currentBook = currentBook.copy(driveFileId = fileId)
                        bookRepository.updateBook(currentBook)
                    }
                }
            }

            // Collect ALL data including deleted items
            val progress = readingProgressRepository.getProgressIncludingDeleted(currentBook.uuid)
            val bookmarks = bookmarkRepository.getAllBookmarksForBookNow(currentBook.uuid)
            val highlights = highlightRepository.getAllHighlightsForBookNow(currentBook.uuid)
            val notes = noteRepository.getAllNotesForBookNow(currentBook.uuid)

            Log.d(TAG, "pushToRemote: progress for ${currentBook.uuid}: ${progress?.percentage}, bookmarks=${bookmarks.size}, highlights=${highlights.size}, notes=${notes.size}")

            val now = System.currentTimeMillis()
            val updatedBook = currentBook.copy(updatedAt = now)
            val metadata = buildBookMetadata(updatedBook, progress, bookmarks, highlights, notes)
            val metadataJson = gson.toJson(metadata)
            val uploadResult = driveClient.uploadFile(
                bookFolderId,
                "metadata.json",
                metadataJson.toByteArray(),
                "application/json"
            )
            Log.d(TAG, "pushToRemote: metadata upload result=$uploadResult, folderId=$bookFolderId, jsonLen=${metadataJson.length}")

            // Touch folder so its modifiedTime reflects this update,
            // enabling incremental pull to detect the change
            driveClient.touchFolder(bookFolderId)

            // Update lastSyncedAt (preserves driveFileId from currentBook)
            bookRepository.updateBook(updatedBook.copy(lastSyncedAt = now))
        }
    }

    // ── Pull ────────────────────────────────────────────────────────

    suspend fun pullFromRemote() {
        val appFolderId = driveClient.getAppFolderId()
        val remoteFolders = driveClient.listFilesInFolder(appFolderId)
        Log.d(TAG, "pullFromRemote: appFolderId=$appFolderId, remoteFolders=${remoteFolders.size}")
        remoteFolders.forEach { fi ->
            Log.d(TAG, "pullFromRemote: found remote folder: name=${fi.name}, id=${fi.id}, modifiedTime=${fi.modifiedTime}")
        }

        for (fi in remoteFolders) {
            val folderName = fi.name
            val folderId = fi.id
            if (!folderName.startsWith("book_")) continue
            Log.d(TAG, "pullFromRemote: processing folder=$folderName, folderId=$folderId")

            // Download metadata.json from this folder
            val metadataFileId = driveClient.findFileInFolder(folderId, "metadata.json")
            if (metadataFileId == null) {
                Log.w(TAG, "pullFromRemote: no metadata.json found in folder $folderName")
                continue
            }
            val metadataBytes = driveClient.downloadFile(metadataFileId)
            if (metadataBytes == null) {
                Log.w(TAG, "pullFromRemote: failed to download metadata.json from folder $folderName")
                continue
            }
            val metadataJson = String(metadataBytes)
            val metadata = try {
                gson.fromJson(metadataJson, BookMetadata::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "pullFromRemote: failed to parse metadata for $folderName", e)
                continue
            }

            val bookUuid = metadata.bookUuid ?: folderName.removePrefix("book_")
            Log.d(TAG, "pullFromRemote: folder=$folderName, bookUuid=$bookUuid, metadata.bookUuid=${metadata.bookUuid}, metadata.isDeleted=${metadata.isDeleted}")

            // Skip old-format folders (pre-UUID, numeric IDs like book_1, book_2)
            if (metadata.bookUuid == null) {
                Log.w(TAG, "pullFromRemote: skipping old-format folder $folderName (no bookUuid in metadata)")
                continue
            }

            // Find local book by uuid
            val localBook = bookRepository.getBookByUuid(bookUuid)

            if (localBook == null && !metadata.isDeleted) {
                // New book from remote — download it
                Log.d(TAG, "pullFromRemote: new book from remote, downloading: ${metadata.title}")
                downloadBookFromRemote(folderId, folderName, metadata)
            } else if (localBook != null) {
                // Existing local book — merge
                Log.d(TAG, "pullFromRemote: existing local book, merging: ${metadata.title}")
                mergeLocalBook(localBook, metadata, folderId)
            } else {
                Log.d(TAG, "pullFromRemote: skipping deleted book or already exists: bookUuid=$bookUuid")
            }
        }
    }

    // ── Merge ───────────────────────────────────────────────────────

    private suspend fun mergeLocalBook(
        localBook: Book,
        metadata: BookMetadata,
        driveFolderId: String
    ) {
        val now = System.currentTimeMillis()

        // 1. LWW on book itself
        if (metadata.updatedAt > localBook.updatedAt) {
            val merged = localBook.copy(
                title = metadata.title,
                author = metadata.author,
                updatedAt = metadata.updatedAt,
                isDeleted = metadata.isDeleted,
                folderId = metadata.folderId,
                driveFileId = driveFolderId,
                lastSyncedAt = now
            )
            bookRepository.updateBook(merged)
            recordLog(
                bookUuid = localBook.uuid,
                entityType = "book",
                entityUuid = localBook.uuid,
                action = if (metadata.isDeleted) "soft_delete_remote" else "update_remote",
                localUpdatedAt = localBook.updatedAt,
                remoteUpdatedAt = metadata.updatedAt
            )
            Log.d(TAG, "mergeLocalBook: applied remote book update for ${localBook.uuid}")
        } else {
            // Still update driveFileId and lastSyncedAt even if local is newer
            bookRepository.updateBook(
                localBook.copy(driveFileId = driveFolderId, lastSyncedAt = now)
            )
        }

        // If the book is now deleted (either from remote or local), skip annotation merge
        val currentBook = bookRepository.getBookByUuid(localBook.uuid)
        if (currentBook?.isDeleted == true) return

        // Extract cover if missing and book file is EPUB
        if (currentBook != null && (currentBook.coverPath == null || !File(currentBook.coverPath).exists())) {
            if (currentBook.format == BookFormat.EPUB) {
                val epubFile = File(currentBook.filePath)
                if (epubFile.exists()) {
                    try {
                        val coverPath = epubParser.parse(epubFile).coverPath
                        if (coverPath != null) {
                            bookRepository.updateBook(currentBook.copy(coverPath = coverPath))
                            Log.d(TAG, "mergeLocalBook: extracted cover for ${currentBook.uuid}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "mergeLocalBook: failed to extract cover for ${currentBook.uuid}", e)
                    }
                }
            }
        }

        // 2. Merge progress (special: take higher percentage)
        Log.d(TAG, "mergeLocalBook: remote progress for ${localBook.uuid}: ${metadata.progress?.percentage}, uuid=${metadata.progress?.uuid}")
        metadata.progress?.let { remoteProgress ->
            mergeProgress(localBook.uuid, remoteProgress)
        }

        // 3. Merge annotations (bookmarks, highlights, notes) — pure LWW by uuid
        mergeAnnotations(
            bookUuid = localBook.uuid,
            remoteBookmarks = metadata.bookmarks,
            remoteHighlights = metadata.highlights,
            remoteNotes = metadata.notes
        )
    }

    private suspend fun mergeProgress(bookUuid: String, remote: ProgressMetadata) {
        val local = readingProgressRepository.getProgressIncludingDeleted(bookUuid)
        // Always use local uuid to avoid duplicate records (progress is 1-per-book)
        val progressUuid = local?.uuid ?: remote.uuid

        when {
            // Remote deleted, local not deleted -> keep local
            remote.isDeleted && (local == null || !local.isDeleted) -> {
                Log.d(TAG, "mergeProgress: remote deleted, keeping local for $bookUuid")
                return
            }
            // Local deleted, remote not deleted -> restore from remote
            (local == null || local.isDeleted) && !remote.isDeleted -> {
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        uuid = progressUuid,
                        bookUuid = bookUuid,
                        chapterIndex = remote.chapterIndex,
                        charOffset = remote.charOffset,
                        percentage = remote.percentage,
                        updatedAt = remote.updatedAt,
                        isDeleted = false
                    )
                )
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "progress",
                    entityUuid = progressUuid,
                    action = "restore_remote",
                    localUpdatedAt = local?.updatedAt,
                    remoteUpdatedAt = remote.updatedAt
                )
                Log.d(TAG, "mergeProgress: restored from remote for $bookUuid")
            }
            // Both active -> take higher percentage; if equal, take newer updatedAt
            local != null && !local.isDeleted && !remote.isDeleted -> {
                if (remote.percentage > local.percentage ||
                    (remote.percentage == local.percentage && remote.updatedAt > local.updatedAt)
                ) {
                    readingProgressRepository.saveProgress(
                        ReadingProgress(
                            uuid = progressUuid,
                            bookUuid = bookUuid,
                            chapterIndex = remote.chapterIndex,
                            charOffset = remote.charOffset,
                            percentage = remote.percentage,
                            updatedAt = remote.updatedAt,
                            isDeleted = false
                        )
                    )
                    recordLog(
                        bookUuid = bookUuid,
                        entityType = "progress",
                        entityUuid = progressUuid,
                        action = "update_remote",
                        localUpdatedAt = local.updatedAt,
                        remoteUpdatedAt = remote.updatedAt
                    )
                    Log.d(TAG, "mergeProgress: remote ${remote.percentage}% > local ${local.percentage}%, applied remote")
                } else {
                    Log.d(TAG, "mergeProgress: local ${local.percentage}% >= remote ${remote.percentage}%, kept local")
                }
            }
        }
    }

    private suspend fun mergeAnnotations(
        bookUuid: String,
        remoteBookmarks: List<BookmarkMetadata>,
        remoteHighlights: List<HighlightMetadata>,
        remoteNotes: List<NoteMetadata>
    ) {
        // Build local maps by uuid (including deleted)
        val localBookmarks = bookmarkRepository.getAllBookmarksForBookNow(bookUuid)
            .associateBy { it.uuid }
        val localHighlights = highlightRepository.getAllHighlightsForBookNow(bookUuid)
            .associateBy { it.uuid }
        val localNotes = noteRepository.getAllNotesForBookNow(bookUuid)
            .associateBy { it.uuid }

        // Merge bookmarks — LWW by uuid
        for (remoteBm in remoteBookmarks) {
            val localBm = localBookmarks[remoteBm.uuid]
            mergeSingleBookmark(bookUuid, remoteBm, localBm)
        }

        // Merge highlights — LWW by uuid
        for (remoteHl in remoteHighlights) {
            val localHl = localHighlights[remoteHl.uuid]
            mergeSingleHighlight(bookUuid, remoteHl, localHl)
        }

        // Merge notes — LWW by uuid
        for (remoteNt in remoteNotes) {
            val localNt = localNotes[remoteNt.uuid]
            mergeSingleNote(bookUuid, remoteNt, localNt)
        }
    }

    private suspend fun mergeSingleBookmark(
        bookUuid: String,
        remote: BookmarkMetadata,
        local: Bookmark?
    ) {
        when {
            // Remote deleted, local exists and not deleted -> soft delete local
            remote.isDeleted && local != null && !local.isDeleted -> {
                bookmarkRepository.softDeleteBookmark(remote.uuid)
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "bookmark",
                    entityUuid = remote.uuid,
                    action = "soft_delete_remote",
                    localUpdatedAt = local.updatedAt,
                    remoteUpdatedAt = remote.updatedAt
                )
            }
            // Remote not deleted, no local match -> insert
            !remote.isDeleted && local == null -> {
                bookmarkRepository.addBookmark(
                    Bookmark(
                        uuid = remote.uuid,
                        bookUuid = bookUuid,
                        chapterIndex = remote.chapterIndex,
                        charOffset = remote.charOffset,
                        name = remote.name,
                        createdAt = remote.createdAt,
                        updatedAt = remote.updatedAt,
                        isDeleted = false
                    )
                )
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "bookmark",
                    entityUuid = remote.uuid,
                    action = "insert_remote",
                    localUpdatedAt = null,
                    remoteUpdatedAt = remote.updatedAt
                )
            }
            // Remote not deleted, local exists -> LWW on updatedAt
            !remote.isDeleted && local != null -> {
                if (remote.updatedAt > local.updatedAt) {
                    bookmarkRepository.addBookmark(
                        Bookmark(
                            uuid = remote.uuid,
                            bookUuid = bookUuid,
                            chapterIndex = remote.chapterIndex,
                            charOffset = remote.charOffset,
                            name = remote.name,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                            isDeleted = local.isDeleted // keep local soft-delete state
                        )
                    )
                    recordLog(
                        bookUuid = bookUuid,
                        entityType = "bookmark",
                        entityUuid = remote.uuid,
                        action = "update_remote",
                        localUpdatedAt = local.updatedAt,
                        remoteUpdatedAt = remote.updatedAt
                    )
                }
            }
            // Remote deleted, no local or local already deleted -> nothing to do
        }
    }

    private suspend fun mergeSingleHighlight(
        bookUuid: String,
        remote: HighlightMetadata,
        local: Highlight?
    ) {
        when {
            // Remote deleted, local exists and not deleted -> soft delete local
            remote.isDeleted && local != null && !local.isDeleted -> {
                highlightRepository.softDeleteHighlight(remote.uuid)
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "highlight",
                    entityUuid = remote.uuid,
                    action = "soft_delete_remote",
                    localUpdatedAt = local.updatedAt,
                    remoteUpdatedAt = remote.updatedAt
                )
            }
            // Remote not deleted, no local match -> insert
            !remote.isDeleted && local == null -> {
                highlightRepository.addHighlight(
                    Highlight(
                        uuid = remote.uuid,
                        bookUuid = bookUuid,
                        chapterIndex = remote.chapterIndex,
                        startOffset = remote.startOffset,
                        endOffset = remote.endOffset,
                        color = remote.color.toLong(),
                        note = remote.note,
                        createdAt = remote.createdAt,
                        updatedAt = remote.updatedAt,
                        isDeleted = false
                    )
                )
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "highlight",
                    entityUuid = remote.uuid,
                    action = "insert_remote",
                    localUpdatedAt = null,
                    remoteUpdatedAt = remote.updatedAt
                )
            }
            // Remote not deleted, local exists -> LWW on updatedAt
            !remote.isDeleted && local != null -> {
                if (remote.updatedAt > local.updatedAt) {
                    highlightRepository.addHighlight(
                        Highlight(
                            uuid = remote.uuid,
                            bookUuid = bookUuid,
                            chapterIndex = remote.chapterIndex,
                            startOffset = remote.startOffset,
                            endOffset = remote.endOffset,
                            color = remote.color.toLong(),
                            note = remote.note,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                            isDeleted = local.isDeleted
                        )
                    )
                    recordLog(
                        bookUuid = bookUuid,
                        entityType = "highlight",
                        entityUuid = remote.uuid,
                        action = "update_remote",
                        localUpdatedAt = local.updatedAt,
                        remoteUpdatedAt = remote.updatedAt
                    )
                }
            }
        }
    }

    private suspend fun mergeSingleNote(
        bookUuid: String,
        remote: NoteMetadata,
        local: Note?
    ) {
        when {
            // Remote deleted, local exists and not deleted -> soft delete local
            remote.isDeleted && local != null && !local.isDeleted -> {
                noteRepository.softDeleteNote(remote.uuid)
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "note",
                    entityUuid = remote.uuid,
                    action = "soft_delete_remote",
                    localUpdatedAt = local.updatedAt,
                    remoteUpdatedAt = remote.updatedAt
                )
            }
            // Remote not deleted, no local match -> insert
            !remote.isDeleted && local == null -> {
                noteRepository.addNote(
                    Note(
                        uuid = remote.uuid,
                        bookUuid = bookUuid,
                        highlightUuid = remote.highlightUuid,
                        chapterIndex = remote.chapterIndex,
                        charOffset = remote.charOffset,
                        content = remote.content,
                        createdAt = remote.createdAt,
                        updatedAt = remote.updatedAt,
                        isDeleted = false
                    )
                )
                recordLog(
                    bookUuid = bookUuid,
                    entityType = "note",
                    entityUuid = remote.uuid,
                    action = "insert_remote",
                    localUpdatedAt = null,
                    remoteUpdatedAt = remote.updatedAt
                )
            }
            // Remote not deleted, local exists -> LWW on updatedAt
            !remote.isDeleted && local != null -> {
                if (remote.updatedAt > local.updatedAt) {
                    noteRepository.addNote(
                        Note(
                            uuid = remote.uuid,
                            bookUuid = bookUuid,
                            highlightUuid = remote.highlightUuid,
                            chapterIndex = remote.chapterIndex,
                            charOffset = remote.charOffset,
                            content = remote.content,
                            createdAt = remote.createdAt,
                            updatedAt = remote.updatedAt,
                            isDeleted = local.isDeleted
                        )
                    )
                    recordLog(
                        bookUuid = bookUuid,
                        entityType = "note",
                        entityUuid = remote.uuid,
                        action = "update_remote",
                        localUpdatedAt = local.updatedAt,
                        remoteUpdatedAt = remote.updatedAt
                    )
                }
            }
        }
    }

    // ── Folder Sync ─────────────────────────────────────────────────

    private suspend fun syncFolders() {
        try {
            val appFolderId = driveClient.getAppFolderId()

            // Download remote folders.json
            val remoteFolderFileId = driveClient.findFileInFolder(appFolderId, FOLDERS_FILENAME)
            val remoteFolders = if (remoteFolderFileId != null) {
                val bytes = driveClient.downloadFile(remoteFolderFileId)
                if (bytes != null) {
                    val json = String(bytes)
                    val data = gson.fromJson(json, FolderSyncData::class.java)
                    data.folders
                } else emptyList()
            } else emptyList()

            // Get all local folders (including deleted)
            val localFolders = folderRepository.getAllFoldersIncludingDeleted()

            // LWW merge
            val allUuids = (remoteFolders.map { it.uuid } + localFolders.map { it.uuid }).toSet()
            val merged = mutableListOf<FolderMetadata>()

            for (uuid in allUuids) {
                val remote = remoteFolders.find { it.uuid == uuid }
                val local = localFolders.find { it.uuid == uuid }

                val result = when {
                    remote != null && local != null -> {
                        if (remote.updatedAt > local.updatedAt) remote
                        else FolderMetadata(
                            uuid = local.uuid,
                            name = local.name,
                            createdAt = local.createdAt,
                            updatedAt = local.updatedAt,
                            isDeleted = local.isDeleted,
                            driveFileId = null
                        )
                    }
                    remote != null -> remote
                    local != null -> FolderMetadata(
                        uuid = local.uuid,
                        name = local.name,
                        createdAt = local.createdAt,
                        updatedAt = local.updatedAt,
                        isDeleted = local.isDeleted,
                        driveFileId = null
                    )
                    else -> null
                }

                if (result != null && !result.isDeleted) {
                    // Write or update local
                    val existing = folderRepository.getFolderByUuid(result.uuid)
                    if (existing != null) {
                        if (result.updatedAt > existing.updatedAt) {
                            folderRepository.updateFolder(Folder(
                                uuid = result.uuid,
                                name = result.name,
                                createdAt = result.createdAt,
                                updatedAt = result.updatedAt,
                                isDeleted = result.isDeleted
                            ))
                        }
                    } else {
                        folderRepository.addFolder(Folder(
                            uuid = result.uuid,
                            name = result.name,
                            createdAt = result.createdAt,
                            updatedAt = result.updatedAt,
                            isDeleted = result.isDeleted
                        ))
                    }
                } else if (result != null && result.isDeleted) {
                    // Soft delete local
                    val existing = folderRepository.getFolderByUuid(result.uuid)
                    if (existing != null && !existing.isDeleted) {
                        folderRepository.softDeleteFolder(result.uuid)
                    }
                }

                if (result != null) merged.add(result)
            }

            // Build final merged list from both remote and local
            val localMerged = localFolders.map {
                FolderMetadata(
                    uuid = it.uuid,
                    name = it.name,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt,
                    isDeleted = it.isDeleted
                )
            }
            val finalMerged = (merged + localMerged)
                .groupBy { it.uuid }
                .mapValues { (_, items) -> items.maxByOrNull { it.updatedAt }!! }
                .values
                .toList()

            val syncData = FolderSyncData(folders = finalMerged)
            val json = gson.toJson(syncData)
            driveClient.uploadFile(
                appFolderId,
                FOLDERS_FILENAME,
                json.toByteArray(),
                "application/json"
            )
            Log.d(TAG, "syncFolders: completed, merged ${finalMerged.size} folders")
        } catch (e: Exception) {
            Log.e(TAG, "syncFolders: failed", e)
        }
    }

    // ── Clean Deleted Books from Drive ─────────────────────────────

    data class CleanResult(
        val cleanedCount: Int,
        val cleanedSize: Long
    )

    data class DriveDeletedBook(
        val uuid: String,
        val folderId: String,
        val fileSize: Long
    )

    /**
     * Scan remote Drive folders for deleted books.
     * Returns list of books whose metadata has isDeleted=true.
     * Does NOT depend on local database records.
     */
    suspend fun scanDeletedRemoteBooks(): List<DriveDeletedBook> {
        if (!authManager.isSignedIn) {
            Log.w(TAG, "scanDeletedRemoteBooks: not signed in")
            return emptyList()
        }

        Log.d(TAG, "scanDeletedRemoteBooks: starting scan")
        val appFolderId = driveClient.getAppFolderId()
        val remoteFolders = driveClient.listFilesInFolder(appFolderId)
        Log.d(TAG, "scanDeletedRemoteBooks: found ${remoteFolders.size} remote folders")
        val result = mutableListOf<DriveDeletedBook>()

        for (fi in remoteFolders) {
            if (!fi.name.startsWith("book_")) continue
            val metadataFileId = driveClient.findFileInFolder(fi.id, "metadata.json")
            if (metadataFileId == null) {
                Log.d(TAG, "scanDeletedRemoteBooks: no metadata.json in ${fi.name}")
                continue
            }
            val bytes = driveClient.downloadFile(metadataFileId) ?: continue
            val metadata = try {
                gson.fromJson(String(bytes), BookMetadata::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "scanDeletedRemoteBooks: failed to parse metadata in ${fi.name}", e)
                continue
            }
            Log.d(TAG, "scanDeletedRemoteBooks: ${fi.name} isDeleted=${metadata.isDeleted}, title=${metadata.title}")
            if (metadata.isDeleted && metadata.bookUuid != null) {
                result.add(DriveDeletedBook(
                    uuid = metadata.bookUuid,
                    folderId = fi.id,
                    fileSize = metadata.fileSize
                ))
            }
        }
        Log.d(TAG, "scanDeletedRemoteBooks: found ${result.size} deleted books")
        return result
    }

    suspend fun cleanDeletedRemoteBooks(): CleanResult {
        if (!authManager.isSignedIn) {
            throw Exception("Not signed in")
        }

        syncMutex.withLock {
            try {
                _syncStatus.value = SyncStatus.Syncing

                // Scan Drive directly for deleted books
                val deletedOnDrive = scanDeletedRemoteBooks()
                Log.d(TAG, "cleanDeletedRemoteBooks: found ${deletedOnDrive.size} deleted books on Drive")

                var cleanedCount = 0
                var cleanedSize = 0L

                for (book in deletedOnDrive) {
                    Log.d(TAG, "cleanDeletedRemoteBooks: deleting Drive folder book_${book.uuid} (${book.folderId})")
                    driveClient.deleteFile(book.folderId)
                    cleanedSize += book.fileSize
                    cleanedCount++

                    // Also hard-delete local record if it exists
                    val localBook = bookRepository.getBookByUuid(book.uuid)
                    if (localBook != null) {
                        readingProgressRepository.hardDeleteByBook(book.uuid)
                        bookmarkRepository.hardDeleteByBook(book.uuid)
                        highlightRepository.hardDeleteByBook(book.uuid)
                        noteRepository.hardDeleteByBook(book.uuid)
                        bookRepository.hardDeleteBook(book.uuid)
                        Log.d(TAG, "cleanDeletedRemoteBooks: hard-deleted local record for ${book.uuid}")
                    }
                }

                _syncStatus.value = SyncStatus.Success
                Log.d(TAG, "cleanDeletedRemoteBooks: completed, cleaned=$cleanedCount, size=$cleanedSize")
                return CleanResult(cleanedCount, cleanedSize)
            } catch (e: Exception) {
                Log.e(TAG, "cleanDeletedRemoteBooks: failed", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "清理失败")
                throw e
            }
        }
    }

    // ── Sync Log ────────────────────────────────────────────────────

    private suspend fun recordLog(
        bookUuid: String,
        entityType: String,
        entityUuid: String,
        action: String,
        localUpdatedAt: Long?,
        remoteUpdatedAt: Long?
    ) {
        syncLogDao.insert(
            SyncLogEntity(
                bookUuid = bookUuid,
                entityType = entityType,
                entityUuid = entityUuid,
                action = action,
                localUpdatedAt = localUpdatedAt,
                remoteUpdatedAt = remoteUpdatedAt,
                resolvedAt = System.currentTimeMillis()
            )
        )
        syncLogDao.pruneOldLogs()
    }

    // ── Build Metadata ──────────────────────────────────────────────

    private fun buildBookMetadata(
        book: Book,
        progress: ReadingProgress?,
        bookmarks: List<Bookmark>,
        highlights: List<Highlight>,
        notes: List<Note>
    ): BookMetadata {
        return BookMetadata(
            version = 3,
            bookUuid = book.uuid,
            title = book.title,
            author = book.author,
            format = book.format.name,
            fileSize = book.fileSize,
            coverPath = book.coverPath,
            updatedAt = book.updatedAt,
            isDeleted = book.isDeleted,
            folderId = book.folderId,
            progress = progress?.let {
                ProgressMetadata(
                    uuid = it.uuid,
                    chapterIndex = it.chapterIndex,
                    charOffset = it.charOffset,
                    percentage = it.percentage,
                    updatedAt = it.updatedAt,
                    isDeleted = it.isDeleted
                )
            },
            bookmarks = bookmarks.map { bm ->
                BookmarkMetadata(
                    uuid = bm.uuid,
                    chapterIndex = bm.chapterIndex,
                    charOffset = bm.charOffset,
                    name = bm.name,
                    createdAt = bm.createdAt,
                    updatedAt = bm.updatedAt,
                    isDeleted = bm.isDeleted
                )
            },
            highlights = highlights.map { hl ->
                HighlightMetadata(
                    uuid = hl.uuid,
                    chapterIndex = hl.chapterIndex,
                    startOffset = hl.startOffset,
                    endOffset = hl.endOffset,
                    color = hl.color.toInt(),
                    note = hl.note,
                    createdAt = hl.createdAt,
                    updatedAt = hl.updatedAt,
                    isDeleted = hl.isDeleted
                )
            },
            notes = notes.map { nt ->
                NoteMetadata(
                    uuid = nt.uuid,
                    highlightUuid = nt.highlightUuid,
                    chapterIndex = nt.chapterIndex,
                    charOffset = nt.charOffset,
                    content = nt.content,
                    createdAt = nt.createdAt,
                    updatedAt = nt.updatedAt,
                    isDeleted = nt.isDeleted
                )
            }
        )
    }

    // ── Download New Book ───────────────────────────────────────────

    private suspend fun downloadBookFromRemote(
        folderId: String,
        folderName: String,
        metadata: BookMetadata
    ) {
        Log.d(TAG, "downloadBookFromRemote: folderId=$folderId, bookUuid=${metadata.bookUuid}, title=${metadata.title}")

        // 1. Find and download the book file from Drive
        val filesInFolder = driveClient.listFilesInFolder(folderId)
        Log.d(TAG, "downloadBookFromRemote: files in folder: ${filesInFolder.map { it.name }}")
        val bookFileEntry = filesInFolder.firstOrNull { fi ->
            !fi.name.startsWith("metadata") && (fi.name.endsWith(".epub") || fi.name.endsWith(".txt"))
        }
        if (bookFileEntry == null) {
            Log.w(TAG, "downloadBookFromRemote: no book file found in folder $folderName")
            return
        }

        val extension = bookFileEntry.name.substringAfterLast('.').lowercase()
        val format = when (extension) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            else -> {
                Log.w(TAG, "downloadBookFromRemote: unsupported format: $extension")
                return
            }
        }

        // 2. Download to local books directory
        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val localFileName = "${UUID.randomUUID()}.$extension"
        val localFile = File(booksDir, localFileName)
        driveClient.downloadFileTo(bookFileEntry.id, localFile)
        if (!localFile.exists()) {
            Log.e(TAG, "downloadBookFromRemote: download failed, file not created: ${localFile.absolutePath}")
            return
        }
        Log.d(TAG, "downloadBookFromRemote: downloaded file ${localFile.absolutePath}, size=${localFile.length()}")

        // 3. Extract cover image from EPUB
        val coverPath = if (format == BookFormat.EPUB) {
            try {
                epubParser.parse(localFile).coverPath
            } catch (e: Exception) {
                Log.w(TAG, "downloadBookFromRemote: failed to extract cover", e)
                null
            }
        } else null

        // 4. Create book record via repository using remote uuid
        val book = Book(
            uuid = metadata.bookUuid,
            title = metadata.title,
            author = metadata.author,
            filePath = localFile.absolutePath,
            format = format,
            coverPath = coverPath,
            fileSize = localFile.length(),
            lastSyncedAt = System.currentTimeMillis(),
            folderId = metadata.folderId,
            driveFileId = folderId
        )
        bookRepository.addBook(book)

        // 4. Apply remote progress
        metadata.progress?.let { prog ->
            if (!prog.isDeleted) {
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        uuid = prog.uuid,
                        bookUuid = metadata.bookUuid,
                        chapterIndex = prog.chapterIndex,
                        charOffset = prog.charOffset,
                        percentage = prog.percentage,
                        updatedAt = prog.updatedAt,
                        isDeleted = false
                    )
                )
            }
        }

        // 5. Apply remote bookmarks
        for (bm in metadata.bookmarks) {
            if (!bm.isDeleted) {
                bookmarkRepository.addBookmark(
                    Bookmark(
                        uuid = bm.uuid,
                        bookUuid = metadata.bookUuid,
                        chapterIndex = bm.chapterIndex,
                        charOffset = bm.charOffset,
                        name = bm.name,
                        createdAt = bm.createdAt,
                        updatedAt = bm.updatedAt,
                        isDeleted = false
                    )
                )
            }
        }

        // 6. Apply remote highlights
        for (hl in metadata.highlights) {
            if (!hl.isDeleted) {
                highlightRepository.addHighlight(
                    Highlight(
                        uuid = hl.uuid,
                        bookUuid = metadata.bookUuid,
                        chapterIndex = hl.chapterIndex,
                        startOffset = hl.startOffset,
                        endOffset = hl.endOffset,
                        color = hl.color.toLong(),
                        note = hl.note,
                        createdAt = hl.createdAt,
                        updatedAt = hl.updatedAt,
                        isDeleted = false
                    )
                )
            }
        }

        // 7. Apply remote notes
        for (nt in metadata.notes) {
            if (!nt.isDeleted) {
                noteRepository.addNote(
                    Note(
                        uuid = nt.uuid,
                        bookUuid = metadata.bookUuid,
                        highlightUuid = nt.highlightUuid,
                        chapterIndex = nt.chapterIndex,
                        charOffset = nt.charOffset,
                        content = nt.content,
                        createdAt = nt.createdAt,
                        updatedAt = nt.updatedAt,
                        isDeleted = false
                    )
                )
            }
        }

        Log.d(TAG, "downloadBookFromRemote: downloaded book ${metadata.title} with uuid ${metadata.bookUuid}")
    }
}
