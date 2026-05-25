package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.local.dao.ConflictDao
import com.ebookreader.simplebook.data.local.entity.ConflictRecordEntity
import com.ebookreader.simplebook.data.remote.AuthManager
import com.ebookreader.simplebook.data.remote.BookMetadata
import com.ebookreader.simplebook.data.remote.GoogleDriveClient
import com.ebookreader.simplebook.data.remote.ProgressMetadata
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.Bookmark
import com.ebookreader.simplebook.domain.model.Highlight
import com.ebookreader.simplebook.domain.model.Note
import com.ebookreader.simplebook.domain.model.ReadingProgress
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val driveClient: GoogleDriveClient,
    private val authManager: AuthManager,
    private val bookRepository: BookRepository,
    private val readingProgressRepository: ReadingProgressRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val highlightRepository: HighlightRepository,
    private val noteRepository: NoteRepository,
    private val conflictDao: ConflictDao,
    private val gson: Gson
) {
    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    private val _conflictCount = MutableStateFlow(0)
    val conflictCount: StateFlow<Int> = _conflictCount.asStateFlow()

    private val syncMutex = Mutex()

    suspend fun syncAll() {
        if (!authManager.isSignedIn) {
            _syncStatus.value = SyncStatus.Error("Not signed in")
            return
        }

        syncMutex.withLock {
            try {
                _syncStatus.value = SyncStatus.Syncing
                pullFromRemote()
                pushToRemote()
                refreshConflictCount()
                _syncStatus.value = SyncStatus.Success
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Error(e.message ?: "Sync failed")
            }
        }
    }

    suspend fun pushToRemote() {
        val appFolderId = driveClient.getAppFolderId() ?: throw Exception("Cannot access app folder")
        val books = bookRepository.getAllBooksNow()

        for (book in books) {
            val hasLocalChanges = book.lastSyncedAt == null ||
                (book.syncVersion > 1 && book.lastSyncedAt != null)

            if (!hasLocalChanges) continue

            val bookFolderName = "book_${book.id}"
            val bookFolderId = driveClient.createFolder(bookFolderName, appFolderId)
                ?: throw Exception("Failed to create book folder")

            // Upload book file on first sync
            if (book.driveFileId == null) {
                val fileName = "${book.title}.${book.format.name.lowercase()}"
                val localFile = java.io.File(book.filePath)
                if (localFile.exists()) {
                    val mimeType = when (book.format) {
                        com.ebookreader.simplebook.domain.model.BookFormat.EPUB -> "application/epub+zip"
                        com.ebookreader.simplebook.domain.model.BookFormat.TXT -> "text/plain"
                    }
                    val fileId = driveClient.uploadBookFile(bookFolderId, fileName, localFile, mimeType)
                    if (fileId != null) {
                        bookRepository.updateBook(book.copy(driveFileId = fileId))
                    }
                }
            }

            // Build and upload metadata
            val progress = readingProgressRepository.getProgress(book.id)
            val bookmarks = bookmarkRepository.getBookmarksForBookNow(book.id)
            val highlights = highlightRepository.getHighlightsForBookNow(book.id)
            val notes = noteRepository.getNotesForBookNow(book.id)

            val metadata = buildBookMetadata(book, progress, bookmarks, highlights, notes)
            val metadataJson = gson.toJson(metadata)
            driveClient.uploadFile(
                bookFolderId,
                "metadata.json",
                metadataJson.toByteArray(),
                "application/json"
            )

            // Update lastSyncedAt
            val now = System.currentTimeMillis()
            bookRepository.updateBook(book.copy(lastSyncedAt = now))
        }
    }

    suspend fun pullFromRemote() {
        val appFolderId = driveClient.getAppFolderId() ?: throw Exception("Cannot access app folder")
        val remoteFolders = driveClient.listFilesInFolder(appFolderId)

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
                continue
            }

            // Look for local book matching by driveFileId or by bookId in folder name
            val localBook = findLocalBook(folderName, metadata)

            if (localBook == null) {
                // New book from remote - create a placeholder entry
                // The actual file download will be handled separately by the UI
                val newBook = Book(
                    title = metadata.title,
                    author = metadata.author,
                    filePath = "", // Will be set when file is actually downloaded
                    format = com.ebookreader.simplebook.domain.model.BookFormat.valueOf(metadata.format),
                    fileSize = metadata.fileSize,
                    syncVersion = metadata.syncVersion,
                    lastSyncedAt = System.currentTimeMillis(),
                    driveFileId = folderId
                )
                val newId = bookRepository.addBook(newBook)

                // Apply remote progress
                metadata.progress?.let { prog ->
                    readingProgressRepository.saveProgress(
                        ReadingProgress(
                            bookId = newId,
                            chapterIndex = prog.chapterIndex,
                            charOffset = prog.charOffset,
                            percentage = prog.percentage,
                            updatedAt = prog.updatedAt,
                            syncVersion = prog.syncVersion,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }

                // Apply remote bookmarks
                for (bm in metadata.bookmarks) {
                    bookmarkRepository.addBookmark(
                        Bookmark(
                            bookId = newId,
                            chapterIndex = bm.chapterIndex,
                            charOffset = bm.charOffset,
                            name = bm.name,
                            createdAt = bm.createdAt,
                            syncVersion = bm.syncVersion,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }

                // Apply remote highlights
                for (hl in metadata.highlights) {
                    highlightRepository.addHighlight(
                        Highlight(
                            bookId = newId,
                            chapterIndex = hl.chapterIndex,
                            startOffset = hl.startOffset,
                            endOffset = hl.endOffset,
                            color = hl.color.toLong(),
                            note = hl.note,
                            createdAt = hl.createdAt,
                            syncVersion = hl.syncVersion,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }

                // Apply remote notes
                for (nt in metadata.notes) {
                    noteRepository.addNote(
                        Note(
                            bookId = newId,
                            highlightId = nt.highlightId,
                            chapterIndex = nt.chapterIndex,
                            charOffset = nt.charOffset,
                            content = nt.content,
                            createdAt = nt.createdAt,
                            syncVersion = nt.syncVersion,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
            } else {
                // Local book exists - compare versions and detect conflicts
                mergeLocalBook(localBook, metadata, folderId)
            }
        }

        refreshConflictCount()
    }

    suspend fun resolveConflict(conflictId: Long, useRemote: Boolean) {
        val conflicts = conflictDao.getUnresolvedConflictsNow()
        val conflict = conflicts.find { it.id == conflictId } ?: return

        if (useRemote) {
            applyRemoteData(conflict)
        }

        conflictDao.markResolved(conflict.id, System.currentTimeMillis())
        refreshConflictCount()
    }

    suspend fun resolveAllConflicts(useRemote: Boolean) {
        val conflicts = conflictDao.getUnresolvedConflictsNow()
        for (conflict in conflicts) {
            if (useRemote) {
                applyRemoteData(conflict)
            }
            conflictDao.markResolved(conflict.id, System.currentTimeMillis())
        }
        refreshConflictCount()
    }

    // --- Private helpers ---

    private suspend fun findLocalBook(folderName: String, metadata: BookMetadata): Book? {
        // Try to match by folder name pattern "book_{id}"
        val idFromFolder = folderName.removePrefix("book_").toLongOrNull()
        if (idFromFolder != null) {
            val bookById = bookRepository.getBookById(idFromFolder)
            if (bookById != null) return bookById
        }

        // Try to match by title and author
        val allBooks = bookRepository.getAllBooksNow()
        return allBooks.find { it.title == metadata.title && it.author == metadata.author }
    }

    private suspend fun mergeLocalBook(localBook: Book, metadata: BookMetadata, driveFolderId: String) {
        val remoteVersion = metadata.syncVersion
        val localVersion = localBook.syncVersion
        val localHasChanges = localBook.lastSyncedAt != null &&
            localBook.lastReadAt != null &&
            localBook.lastReadAt!! > (localBook.lastSyncedAt ?: 0)

        if (remoteVersion > localVersion) {
            if (localHasChanges) {
                // Conflict detected
                detectConflicts(localBook, metadata)
            } else {
                // Remote-only update - apply directly
                applyRemoteMetadata(localBook, metadata, driveFolderId)
            }
        }
        // If remoteVersion <= localVersion, local is up to date or ahead, nothing to do
    }

    private suspend fun detectConflicts(localBook: Book, metadata: BookMetadata) {
        val now = System.currentTimeMillis()

        // Check progress conflict
        val localProgress = readingProgressRepository.getProgress(localBook.id)
        if (localProgress != null && metadata.progress != null) {
            if (metadata.progress.syncVersion > localProgress.syncVersion) {
                conflictDao.insert(
                    ConflictRecordEntity(
                        bookId = localBook.id,
                        entityType = "progress",
                        entityId = localProgress.id,
                        localSyncVersion = localProgress.syncVersion,
                        remoteSyncVersion = metadata.progress.syncVersion,
                        localData = gson.toJson(localProgress),
                        remoteData = gson.toJson(metadata.progress),
                        createdAt = now
                    )
                )
            }
        }

        // Check bookmark conflicts
        val localBookmarks = bookmarkRepository.getBookmarksForBookNow(localBook.id)
        for (localBm in localBookmarks) {
            val remoteMatch = metadata.bookmarks.find { rm ->
                rm.chapterIndex == localBm.chapterIndex && rm.charOffset == localBm.charOffset
            }
            if (remoteMatch != null && remoteMatch.syncVersion > localBm.syncVersion) {
                conflictDao.insert(
                    ConflictRecordEntity(
                        bookId = localBook.id,
                        entityType = "bookmark",
                        entityId = localBm.id,
                        localSyncVersion = localBm.syncVersion,
                        remoteSyncVersion = remoteMatch.syncVersion,
                        localData = gson.toJson(localBm),
                        remoteData = gson.toJson(remoteMatch),
                        createdAt = now
                    )
                )
            }
        }

        // Check highlight conflicts
        val localHighlights = highlightRepository.getHighlightsForBookNow(localBook.id)
        for (localHl in localHighlights) {
            val remoteMatch = metadata.highlights.find { rm ->
                rm.chapterIndex == localHl.chapterIndex &&
                    rm.startOffset == localHl.startOffset &&
                    rm.endOffset == localHl.endOffset
            }
            if (remoteMatch != null && remoteMatch.syncVersion > localHl.syncVersion) {
                conflictDao.insert(
                    ConflictRecordEntity(
                        bookId = localBook.id,
                        entityType = "highlight",
                        entityId = localHl.id,
                        localSyncVersion = localHl.syncVersion,
                        remoteSyncVersion = remoteMatch.syncVersion,
                        localData = gson.toJson(localHl),
                        remoteData = gson.toJson(remoteMatch),
                        createdAt = now
                    )
                )
            }
        }

        // Check note conflicts
        val localNotes = noteRepository.getNotesForBookNow(localBook.id)
        for (localNt in localNotes) {
            val remoteMatch = metadata.notes.find { rm ->
                rm.chapterIndex == localNt.chapterIndex &&
                    rm.charOffset == localNt.charOffset &&
                    rm.content == localNt.content
            }
            if (remoteMatch != null && remoteMatch.syncVersion > localNt.syncVersion) {
                conflictDao.insert(
                    ConflictRecordEntity(
                        bookId = localBook.id,
                        entityType = "note",
                        entityId = localNt.id,
                        localSyncVersion = localNt.syncVersion,
                        remoteSyncVersion = remoteMatch.syncVersion,
                        localData = gson.toJson(localNt),
                        remoteData = gson.toJson(remoteMatch),
                        createdAt = now
                    )
                )
            }
        }
    }

    private suspend fun applyRemoteMetadata(localBook: Book, metadata: BookMetadata, driveFolderId: String) {
        val now = System.currentTimeMillis()

        // Update book metadata
        bookRepository.updateBook(
            localBook.copy(
                title = metadata.title,
                author = metadata.author,
                syncVersion = metadata.syncVersion,
                lastSyncedAt = now,
                driveFileId = driveFolderId
            )
        )

        // Apply remote progress
        metadata.progress?.let { prog ->
            val existing = readingProgressRepository.getProgress(localBook.id)
            if (existing == null || prog.syncVersion > existing.syncVersion) {
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        id = existing?.id ?: 0,
                        bookId = localBook.id,
                        chapterIndex = prog.chapterIndex,
                        charOffset = prog.charOffset,
                        percentage = prog.percentage,
                        updatedAt = prog.updatedAt,
                        syncVersion = prog.syncVersion,
                        lastSyncedAt = now
                    )
                )
            }
        }

        // Apply remote bookmarks (upsert by matching chapter + offset)
        val localBookmarks = bookmarkRepository.getBookmarksForBookNow(localBook.id)
        for (bm in metadata.bookmarks) {
            val existing = localBookmarks.find { lb ->
                lb.chapterIndex == bm.chapterIndex && lb.charOffset == bm.charOffset
            }
            if (existing == null) {
                bookmarkRepository.addBookmark(
                    Bookmark(
                        bookId = localBook.id,
                        chapterIndex = bm.chapterIndex,
                        charOffset = bm.charOffset,
                        name = bm.name,
                        createdAt = bm.createdAt,
                        syncVersion = bm.syncVersion,
                        lastSyncedAt = now
                    )
                )
            } else if (bm.syncVersion > existing.syncVersion) {
                bookmarkRepository.addBookmark(
                    existing.copy(
                        name = bm.name,
                        syncVersion = bm.syncVersion,
                        lastSyncedAt = now
                    )
                )
            }
        }

        // Apply remote highlights
        val localHighlights = highlightRepository.getHighlightsForBookNow(localBook.id)
        for (hl in metadata.highlights) {
            val existing = localHighlights.find { lh ->
                lh.chapterIndex == hl.chapterIndex &&
                    lh.startOffset == hl.startOffset &&
                    lh.endOffset == hl.endOffset
            }
            if (existing == null) {
                highlightRepository.addHighlight(
                    Highlight(
                        bookId = localBook.id,
                        chapterIndex = hl.chapterIndex,
                        startOffset = hl.startOffset,
                        endOffset = hl.endOffset,
                        color = hl.color.toLong(),
                        note = hl.note,
                        createdAt = hl.createdAt,
                        syncVersion = hl.syncVersion,
                        lastSyncedAt = now
                    )
                )
            } else if (hl.syncVersion > existing.syncVersion) {
                highlightRepository.addHighlight(
                    existing.copy(
                        color = hl.color.toLong(),
                        note = hl.note,
                        syncVersion = hl.syncVersion,
                        lastSyncedAt = now
                    )
                )
            }
        }

        // Apply remote notes
        val localNotes = noteRepository.getNotesForBookNow(localBook.id)
        for (nt in metadata.notes) {
            val existing = localNotes.find { ln ->
                ln.chapterIndex == nt.chapterIndex && ln.charOffset == nt.charOffset
            }
            if (existing == null) {
                noteRepository.addNote(
                    Note(
                        bookId = localBook.id,
                        highlightId = nt.highlightId,
                        chapterIndex = nt.chapterIndex,
                        charOffset = nt.charOffset,
                        content = nt.content,
                        createdAt = nt.createdAt,
                        syncVersion = nt.syncVersion,
                        lastSyncedAt = now
                    )
                )
            } else if (nt.syncVersion > existing.syncVersion) {
                noteRepository.addNote(
                    existing.copy(
                        content = nt.content,
                        syncVersion = nt.syncVersion,
                        lastSyncedAt = now
                    )
                )
            }
        }
    }

    private suspend fun applyRemoteData(conflict: ConflictRecordEntity) {
        val now = System.currentTimeMillis()
        when (conflict.entityType) {
            "progress" -> {
                val remoteProgress = gson.fromJson(conflict.remoteData, ProgressMetadata::class.java)
                val existing = readingProgressRepository.getProgress(conflict.bookId)
                readingProgressRepository.saveProgress(
                    ReadingProgress(
                        id = existing?.id ?: 0,
                        bookId = conflict.bookId,
                        chapterIndex = remoteProgress.chapterIndex,
                        charOffset = remoteProgress.charOffset,
                        percentage = remoteProgress.percentage,
                        updatedAt = remoteProgress.updatedAt,
                        syncVersion = remoteProgress.syncVersion,
                        lastSyncedAt = now
                    )
                )
            }
            "bookmark" -> {
                val remoteBookmark = gson.fromJson(conflict.remoteData, com.ebookreader.simplebook.data.remote.BookmarkMetadata::class.java)
                val localBookmarks = bookmarkRepository.getBookmarksForBookNow(conflict.bookId)
                val existing = localBookmarks.find { it.id == conflict.entityId }
                if (existing != null) {
                    bookmarkRepository.addBookmark(
                        existing.copy(
                            name = remoteBookmark.name,
                            syncVersion = remoteBookmark.syncVersion,
                            lastSyncedAt = now
                        )
                    )
                }
            }
            "highlight" -> {
                val remoteHighlight = gson.fromJson(conflict.remoteData, com.ebookreader.simplebook.data.remote.HighlightMetadata::class.java)
                val localHighlights = highlightRepository.getHighlightsForBookNow(conflict.bookId)
                val existing = localHighlights.find { it.id == conflict.entityId }
                if (existing != null) {
                    highlightRepository.addHighlight(
                        existing.copy(
                            color = remoteHighlight.color.toLong(),
                            note = remoteHighlight.note,
                            syncVersion = remoteHighlight.syncVersion,
                            lastSyncedAt = now
                        )
                    )
                }
            }
            "note" -> {
                val remoteNote = gson.fromJson(conflict.remoteData, com.ebookreader.simplebook.data.remote.NoteMetadata::class.java)
                val localNotes = noteRepository.getNotesForBookNow(conflict.bookId)
                val existing = localNotes.find { it.id == conflict.entityId }
                if (existing != null) {
                    noteRepository.addNote(
                        existing.copy(
                            content = remoteNote.content,
                            syncVersion = remoteNote.syncVersion,
                            lastSyncedAt = now
                        )
                    )
                }
            }
        }
    }

    private fun buildBookMetadata(
        book: Book,
        progress: ReadingProgress?,
        bookmarks: List<Bookmark>,
        highlights: List<Highlight>,
        notes: List<Note>
    ): BookMetadata {
        return BookMetadata(
            bookId = book.id,
            title = book.title,
            author = book.author,
            format = book.format.name,
            fileSize = book.fileSize,
            coverPath = book.coverPath,
            syncVersion = book.syncVersion,
            updatedAt = book.lastReadAt ?: book.addedAt,
            progress = progress?.let {
                com.ebookreader.simplebook.data.remote.ProgressMetadata(
                    chapterIndex = it.chapterIndex,
                    charOffset = it.charOffset,
                    percentage = it.percentage,
                    syncVersion = it.syncVersion,
                    updatedAt = it.updatedAt
                )
            },
            bookmarks = bookmarks.map { bm ->
                com.ebookreader.simplebook.data.remote.BookmarkMetadata(
                    chapterIndex = bm.chapterIndex,
                    charOffset = bm.charOffset,
                    name = bm.name,
                    syncVersion = bm.syncVersion,
                    createdAt = bm.createdAt
                )
            },
            highlights = highlights.map { hl ->
                com.ebookreader.simplebook.data.remote.HighlightMetadata(
                    chapterIndex = hl.chapterIndex,
                    startOffset = hl.startOffset,
                    endOffset = hl.endOffset,
                    color = hl.color.toInt(),
                    note = hl.note,
                    syncVersion = hl.syncVersion,
                    createdAt = hl.createdAt
                )
            },
            notes = notes.map { nt ->
                com.ebookreader.simplebook.data.remote.NoteMetadata(
                    highlightId = nt.highlightId,
                    chapterIndex = nt.chapterIndex,
                    charOffset = nt.charOffset,
                    content = nt.content,
                    syncVersion = nt.syncVersion,
                    createdAt = nt.createdAt
                )
            }
        )
    }

    private suspend fun refreshConflictCount() {
        val count = conflictDao.getUnresolvedConflictsNow().size
        _conflictCount.value = count
    }
}
