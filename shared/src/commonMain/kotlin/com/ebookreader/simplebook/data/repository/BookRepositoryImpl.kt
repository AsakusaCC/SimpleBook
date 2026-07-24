package com.ebookreader.simplebook.data.repository

import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.entity.BookEntity
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class BookRepositoryImpl constructor(
    private val bookDao: BookDao
) : BookRepository {

    override fun getAllBooks(): Flow<List<Book>> =
        bookDao.getAllBooks().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getAllBooksNow(): List<Book> =
        bookDao.getAllBooksNow().map { it.toDomain() }

    override suspend fun getBookByUuid(uuid: String): Book? =
        bookDao.getBookByUuid(uuid)?.toDomain()

    override suspend fun getBookByDriveFileId(driveFileId: String): Book? =
        bookDao.getBookByDriveFileId(driveFileId)?.toDomain()

    override suspend fun addBook(book: Book): String {
        bookDao.insert(book.toEntity())
        return book.uuid
    }

    override suspend fun updateBook(book: Book) =
        bookDao.update(book.toEntity())

    override suspend fun softDeleteBook(uuid: String) =
        bookDao.softDelete(uuid)

    override suspend fun getAllBooksIncludingDeleted(): List<Book> =
        bookDao.getAllBooksIncludingDeleted().map { it.toDomain() }

    override suspend fun getDirtyBooks(): List<Book> =
        bookDao.getDirtyBooks().map { it.toDomain() }

    override suspend fun hardDeleteBook(uuid: String) =
        bookDao.hardDelete(uuid)

    override fun getShelfBooks(): Flow<List<Book>> =
        bookDao.getShelfBooks().map { entities -> entities.map { it.toDomain() } }

    override fun getBooksInFolder(folderId: String): Flow<List<Book>> =
        bookDao.getBooksInFolder(folderId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun moveBookToFolder(bookUuid: String, folderId: String?) {
        val book = bookDao.getBookByUuid(bookUuid) ?: return
        bookDao.update(book.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    private fun BookEntity.toDomain() = Book(
        uuid = uuid, title = title, author = author, filePath = filePath,
        format = BookFormat.valueOf(format), coverPath = coverPath, fileSize = fileSize,
        addedAt = addedAt, lastReadAt = lastReadAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, folderId = folderId, driveFileId = driveFileId
    )

    private fun Book.toEntity() = BookEntity(
        uuid = uuid, title = title, author = author, filePath = filePath,
        format = format.name, coverPath = coverPath, fileSize = fileSize,
        addedAt = addedAt, lastReadAt = lastReadAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, folderId = folderId, driveFileId = driveFileId
    )
}
