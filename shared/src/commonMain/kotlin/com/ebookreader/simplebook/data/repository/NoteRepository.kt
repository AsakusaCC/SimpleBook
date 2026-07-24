package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepository constructor(
    private val noteDao: NoteDao
) {
    fun getNotesForBook(bookUuid: String): Flow<List<Note>> =
        noteDao.getNotesForBook(bookUuid).map { list -> list.map { it.toDomain() } }

    suspend fun getNotesForBookNow(bookUuid: String): List<Note> =
        noteDao.getNotesForBookNow(bookUuid).map { it.toDomain() }

    suspend fun getAllNotesForBookNow(bookUuid: String): List<Note> =
        noteDao.getAllNotesForBookNow(bookUuid).map { it.toDomain() }

    fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes().map { list -> list.map { it.toDomain() } }

    suspend fun addNote(note: Note) { noteDao.insert(note.toEntity()) }

    suspend fun softDeleteNote(uuid: String) { noteDao.softDelete(uuid) }

    suspend fun hardDeleteByBook(bookUuid: String) { noteDao.hardDeleteByBook(bookUuid) }

    private fun NoteEntity.toDomain() = Note(
        uuid = uuid, bookUuid = bookUuid, highlightUuid = highlightUuid,
        chapterIndex = chapterIndex, charOffset = charOffset, content = content,
        createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )

    private fun Note.toEntity() = NoteEntity(
        uuid = uuid, bookUuid = bookUuid, highlightUuid = highlightUuid,
        chapterIndex = chapterIndex, charOffset = charOffset, content = content,
        createdAt = createdAt, updatedAt = updatedAt, isDeleted = isDeleted
    )
}
