package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.entity.NoteEntity
import com.ebookreader.simplebook.domain.model.Note
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val noteDao: NoteDao
) {
    fun getNotesForBook(bookId: Long): Flow<List<Note>> =
        noteDao.getNotesForBook(bookId).map { list -> list.map { it.toDomain() } }

    fun getAllNotes(): Flow<List<Note>> =
        noteDao.getAllNotes().map { list -> list.map { it.toDomain() } }

    suspend fun addNote(note: Note): Long = noteDao.insert(note.toEntity())
    suspend fun deleteNote(note: Note) = noteDao.delete(note.toEntity())

    private fun NoteEntity.toDomain() = Note(
        id = id,
        bookId = bookId,
        highlightId = highlightId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        content = content,
        createdAt = createdAt
    )

    private fun Note.toEntity() = NoteEntity(
        id = id,
        bookId = bookId,
        highlightId = highlightId,
        chapterIndex = chapterIndex,
        charOffset = charOffset,
        content = content,
        createdAt = createdAt
    )
}
