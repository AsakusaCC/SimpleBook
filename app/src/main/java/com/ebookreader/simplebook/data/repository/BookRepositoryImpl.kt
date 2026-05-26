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

    private fun BookEntity.toDomain() = Book(
        uuid = uuid, title = title, author = author, filePath = filePath,
        format = BookFormat.valueOf(format), coverPath = coverPath, fileSize = fileSize,
        addedAt = addedAt, lastReadAt = lastReadAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, driveFileId = driveFileId
    )

    private fun Book.toEntity() = BookEntity(
        uuid = uuid, title = title, author = author, filePath = filePath,
        format = format.name, coverPath = coverPath, fileSize = fileSize,
        addedAt = addedAt, lastReadAt = lastReadAt, updatedAt = updatedAt,
        isDeleted = isDeleted, lastSyncedAt = lastSyncedAt, driveFileId = driveFileId
    )
}
