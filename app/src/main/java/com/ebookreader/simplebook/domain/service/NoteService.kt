package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.domain.model.Note
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteService @Inject constructor(
    private val noteRepo: NoteRepository
) {
    fun getNotesForBook(bookId: Long): Flow<List<Note>> = noteRepo.getNotesForBook(bookId)
    fun getAllNotes(): Flow<List<Note>> = noteRepo.getAllNotes()
    suspend fun addNote(note: Note): Long = noteRepo.addNote(note)
    suspend fun deleteNote(note: Note) = noteRepo.deleteNote(note)
}
