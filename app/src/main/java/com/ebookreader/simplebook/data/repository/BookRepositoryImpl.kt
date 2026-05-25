package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepositoryImpl @Inject constructor(
    private val bookDao: BookDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getBookById(id: Long): Book? =
        bookDao.getBookById(id)?.toDomain()

    override suspend fun addBook(book: Book): Long =
        bookDao.insert(book.toEntity())

    override suspend fun updateBook(book: Book) =
        bookDao.update(book.toEntity())

    override suspend fun deleteBook(book: Book) =
        bookDao.delete(book.toEntity())

    private fun BookEntity.toDomain() = Book(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        format = BookFormat.valueOf(format),
        coverPath = coverPath,
        fileSize = fileSize,
        addedAt = addedAt,
        lastReadAt = lastReadAt,
        syncVersion = syncVersion,
        lastSyncedAt = lastSyncedAt,
        driveFileId = driveFileId
    )

    private fun Book.toEntity() = BookEntity(
        id = id,
        title = title,
        author = author,
        filePath = filePath,
        format = format.name,
        coverPath = coverPath,
        fileSize = fileSize,
        addedAt = addedAt,
        lastReadAt = lastReadAt,
        syncVersion = syncVersion,
        lastSyncedAt = lastSyncedAt,
        driveFileId = driveFileId
    )
}
