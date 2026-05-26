package com.ebookreader.simplebook.domain.service

import android.content.Context
import android.util.Log
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
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
import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Highlight
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
    private val syncLogDao: SyncLogDao,
    private val gson: Gson
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _lastSyncedAt = MutableStateFlow<Long?>(null)
    val lastSyncedAt: StateFlow<Long?> = _lastSyncedAt.asStateFlow()

    private val syncMutex = Mutex()

    companion object {
        private const val TAG = "SyncService"
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
                val now = System.currentTimeMillis()
                _lastSyncedAt.value = now
                _syncStatus.value = SyncStatus.Success
                Log.d(TAG, "syncAll: completed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "syncAll: failed", e)
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    // ── Push ────────────────────────────────────────────────────────

    suspend fun pushToRemote() {
        val appFolderId = driveClient.getAppFolderId()
        // Push ALL books including soft-deleted ones
        val books = bookRepository.getAllBooksIncludingDeleted()
        Log.d(TAG, "pushToRemote: appFolderId=$appFolderId, books=${books.size}")

        for (book in books) {
            Log.d(TAG, "pushToRemote: pushing book=${book.title}, uuid=${book.uuid}, isDeleted=${book.isDeleted}")

            val bookFolderName = "book_${book.uuid}"
            val bookFolderId = driveClient.createFolder(bookFolderName, appFolderId)
                ?: throw Exception("Failed to create book folder for ${book.uuid}")

            // Upload book file on first sync
            if (book.driveFileId == null) {
                val fileName = "${book.title}.${book.format.name.lowercase()}"
                val localFile = File(book.filePath)
                if (localFile.exists()) {
                    val mimeType = when (book.format) {
                        BookFormat.EPUB -> "application/epub+zip"
                        BookFormat.TXT -> "text/plain"
                    }
                    val fileId = driveClient.uploadBookFile(bookFolderId, fileName, localFile, mimeType)
                    if (fileId != null) {
                        bookRepository.updateBook(book.copy(driveFileId = fileId))
                    }
                }
            }

            // Collect ALL data including deleted items
            val progress = readingProgressRepository.getProgressIncludingDeleted(book.uuid)
            val bookmarks = bookmarkRepository.getAllBookmarksForBookNow(book.uuid)
            val highlights = highlightRepository.getAllHighlightsForBookNow(book.uuid)
            val notes = noteRepository.getAllNotesForBookNow(book.uuid)

            val now = System.currentTimeMillis()
            val updatedBook = book.copy(updatedAt = now)
            val metadata = buildBookMetadata(updatedBook, progress, bookmarks, highlights, notes)
            val metadataJson = gson.toJson(metadata)
            driveClient.uploadFile(
                bookFolderId,
                "metadata.json",
                metadataJson.toByteArray(),
                "application/json"
            )

            // Update lastSyncedAt
            bookRepository.updateBook(updatedBook.copy(lastSyncedAt = now))
        }
    }

    // ── Pull ────────────────────────────────────────────────────────

    suspend fun pullFromRemote() {
        val appFolderId = driveClient.getAppFolderId()
        val remoteFolders = driveClient.listFilesInFolder(appFolderId)
        Log.d(TAG, "pullFromRemote: appFolderId=$appFolderId, remoteFolders=${remoteFolders.size}")

        for ((folderName, folderId) in remoteFolders) {
            if (!folderName.startsWith("book_")) continue

            // Download metadata.json from this folder
            val metadataFileId = driveClient.findFileInFolder(folderId, "metadata.json")
                ?: continue
            val metadataBytes = driveClient.downloadFile(metadataFileId) ?: continue
            val metadataJson = String(metadataBytes)
            val metadata = try {
                gson.fromJson(metadataJson, BookMetadata::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "pullFromRemote: failed to parse metadata for $folderName", e)
                continue
            }

            // Extract bookUuid from folder name: "book_{uuid}"
            val bookUuid = folderName.removePrefix("book_")

            // Find local book by uuid
            val localBook = bookRepository.getBookByUuid(bookUuid)

            if (localBook == null && !metadata.isDeleted) {
                // New book from remote — download it
                downloadBookFromRemote(folderId, folderName, metadata)
            } else if (localBook != null) {
                // Existing local book — merge
                mergeLocalBook(localBook, metadata, folderId)
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

        // 2. Merge progress (special: take higher percentage)
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
                        uuid = remote.uuid,
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
                    entityUuid = remote.uuid,
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
                            uuid = remote.uuid,
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
                        entityUuid = remote.uuid,
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
        // 1. Find and download the book file from Drive
        val filesInFolder = driveClient.listFilesInFolder(folderId)
        val bookFileEntry = filesInFolder.firstOrNull { (name, _) ->
            !name.startsWith("metadata") && (name.endsWith(".epub") || name.endsWith(".txt"))
        } ?: return

        val extension = bookFileEntry.first.substringAfterLast('.').lowercase()
        val format = when (extension) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            else -> return
        }

        // 2. Download to local books directory
        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val localFileName = "${UUID.randomUUID()}.$extension"
        val localFile = File(booksDir, localFileName)
        driveClient.downloadFileTo(bookFileEntry.second, localFile)
        if (!localFile.exists()) return

        // 3. Create book record via repository using remote uuid
        val book = Book(
            uuid = metadata.bookUuid,
            title = metadata.title,
            author = metadata.author,
            filePath = localFile.absolutePath,
            format = format,
            coverPath = metadata.coverPath,
            fileSize = localFile.length(),
            lastSyncedAt = System.currentTimeMillis(),
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
