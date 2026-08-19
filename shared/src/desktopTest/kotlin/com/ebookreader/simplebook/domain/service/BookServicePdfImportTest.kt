package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.data.parser.writeMinimalPdf
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.domain.model.BookFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking

class BookServicePdfImportTest {

    private class FakeBookRepository : BookRepository {
        val added = mutableListOf<Book>()
        override fun getAllBooks(): Flow<List<Book>> = emptyFlow()
        override suspend fun getAllBooksNow(): List<Book> = emptyList()
        override suspend fun getBookByUuid(uuid: String): Book? = null
        override suspend fun getBookByDriveFileId(driveFileId: String): Book? = null
        override suspend fun addBook(book: Book): String { added.add(book); return book.uuid }
        override suspend fun updateBook(book: Book) = error("not used in this test")
        override suspend fun softDeleteBook(uuid: String) = error("not used in this test")
        override suspend fun getAllBooksIncludingDeleted(): List<Book> = error("not used in this test")
        override suspend fun getDirtyBooks(): List<Book> = error("not used in this test")
        override suspend fun hardDeleteBook(uuid: String) = error("not used in this test")
        override fun getShelfBooks(): Flow<List<Book>> = emptyFlow()
        override fun getBooksInFolder(folderId: String): Flow<List<Book>> = emptyFlow()
        override suspend fun moveBookToFolder(bookUuid: String, folderId: String?) = error("not used in this test")
    }

    private fun service(repo: FakeBookRepository) =
        BookService(repo, EpubParser(), TxtParser())

    @Test
    fun importPdfCreatesBookWithCover() {
        runBlocking {
            val repo = FakeBookRepository()
            val pdf = File.createTempFile("import", ".pdf")
            writeMinimalPdf(pdf)
            try {
                val book = service(repo).importBook(pdf, "样例文档")
                assertEquals(1, repo.added.size)
                assertEquals(BookFormat.PDF, book.format)
                assertEquals("样例文档", book.title)
                assertEquals("", book.author)
                assertTrue(book.coverPath != null && File(book.coverPath).exists(),
                    "cover file should exist at ${book.coverPath}")
            } finally {
                pdf.delete()
            }
        }
    }

    @Test
    fun importGarbagePdfThrows() {
        runBlocking {
            val garbage = File.createTempFile("garbage", ".pdf").apply { writeBytes("junk".toByteArray()) }
            try {
                assertFailsWith<IllegalArgumentException> {
                    service(FakeBookRepository()).importBook(garbage, "坏文件")
                }
            } finally {
                garbage.delete()
            }
        }
    }
}
