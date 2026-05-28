package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getAllBooksNow(): List<Book>
    suspend fun getBookByUuid(uuid: String): Book?
    suspend fun getBookByDriveFileId(driveFileId: String): Book?
    suspend fun addBook(book: Book): String
    suspend fun updateBook(book: Book)
    suspend fun softDeleteBook(uuid: String)
    suspend fun getAllBooksIncludingDeleted(): List<Book>
    fun getShelfBooks(): Flow<List<Book>>
    fun getBooksInFolder(folderId: String): Flow<List<Book>>
    suspend fun moveBookToFolder(bookUuid: String, folderId: String?)
}
