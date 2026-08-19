package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.parser.openPdf
import com.ebookreader.simplebook.data.parser.writePdfCover
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import com.ebookreader.simplebook.platform.getBooksDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class BookService constructor(
    private val bookRepository: BookRepository,
    private val epubParser: EpubParser,
    private val txtParser: TxtParser
) {
    fun getAllBooks(): Flow<List<Book>> = bookRepository.getAllBooks()

    suspend fun getAllBooksNow(): List<Book> = bookRepository.getAllBooksNow()

    suspend fun getBookByUuid(uuid: String): Book? = bookRepository.getBookByUuid(uuid)

    suspend fun importBook(file: File, originalName: String = file.nameWithoutExtension): Book {
        val format = when (file.extension.lowercase()) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            "pdf" -> BookFormat.PDF
            else -> throw IllegalArgumentException("Unsupported format: ${file.extension}")
        }

        val title: String
        val author: String
        var coverPath: String? = null

        when (format) {
            BookFormat.EPUB -> {
                val result = epubParser.parse(file)
                    ?: throw IllegalArgumentException("无法解析 EPUB，文件可能已损坏: ${file.name}")
                title = result.title.ifBlank { originalName }
                author = result.author
                coverPath = result.coverPath
            }
            BookFormat.TXT -> {
                val result = txtParser.parse(file)
                title = originalName
                author = result.author
            }
            BookFormat.PDF -> {
                val pageCount = withContext(Dispatchers.IO) {
                    openPdf(file)?.use { it.pageCount } ?: -1
                }
                if (pageCount <= 0) {
                    throw IllegalArgumentException("无法解析 PDF，文件可能已损坏或已加密: ${file.name}")
                }
                title = originalName
                author = ""
                coverPath = withContext(Dispatchers.IO) {
                    writePdfCover(file, File(getBooksDir(), "covers"))
                }?.absolutePath
            }
        }

        val book = Book(
            title = title,
            author = author,
            filePath = file.absolutePath,
            format = format,
            coverPath = coverPath,
            fileSize = file.length()
        )

        bookRepository.addBook(book)
        return book
    }

    suspend fun softDeleteBook(uuid: String) = bookRepository.softDeleteBook(uuid)

    fun getShelfBooks(): Flow<List<Book>> = bookRepository.getShelfBooks()

    fun getBooksInFolder(folderId: String): Flow<List<Book>> = bookRepository.getBooksInFolder(folderId)

    suspend fun moveBookToFolder(bookUuid: String, folderId: String?) =
        bookRepository.moveBookToFolder(bookUuid, folderId)
}
