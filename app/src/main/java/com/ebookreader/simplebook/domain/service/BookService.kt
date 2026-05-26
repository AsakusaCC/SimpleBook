package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookService @Inject constructor(
    private val bookRepository: BookRepository,
    private val epubParser: EpubParser,
    private val txtParser: TxtParser
) {
    fun getAllBooks(): Flow<List<Book>> = bookRepository.getAllBooks()

    suspend fun getBookByUuid(uuid: String): Book? = bookRepository.getBookByUuid(uuid)

    suspend fun importBook(file: File, originalName: String = file.nameWithoutExtension): Book {
        val format = when (file.extension.lowercase()) {
            "epub" -> BookFormat.EPUB
            "txt" -> BookFormat.TXT
            else -> throw IllegalArgumentException("Unsupported format: ${file.extension}")
        }

        val title: String
        val author: String
        var coverPath: String? = null

        when (format) {
            BookFormat.EPUB -> {
                val result = epubParser.parse(file)
                title = result.title.ifBlank { originalName }
                author = result.author
                coverPath = result.coverPath
            }
            BookFormat.TXT -> {
                val result = txtParser.parse(file)
                title = originalName
                author = result.author
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
}
