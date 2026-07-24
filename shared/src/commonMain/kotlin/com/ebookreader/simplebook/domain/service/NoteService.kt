package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.domain.model.Note
import kotlinx.coroutines.flow.Flow

class NoteService constructor(
    private val noteRepo: NoteRepository
) {
    fun getNotesForBook(bookUuid: String): Flow<List<Note>> = noteRepo.getNotesForBook(bookUuid)
    fun getAllNotes(): Flow<List<Note>> = noteRepo.getAllNotes()
    suspend fun addNote(note: Note) = noteRepo.addNote(note)
    suspend fun softDeleteNote(note: Note) = noteRepo.softDeleteNote(note.uuid)
}
