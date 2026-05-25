package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.domain.model.Book
import kotlinx.coroutines.flow.Flow

interface BookRepository {
    fun getAllBooks(): Flow<List<Book>>
    suspend fun getAllBooksNow(): List<Book>
    suspend fun getBookById(id: Long): Book?
    suspend fun getBookByDriveFileId(driveFileId: String): Book?
    suspend fun addBook(book: Book): Long
    suspend fun updateBook(book: Book)
    suspend fun deleteBook(book: Book)
}
