package com.ebookreader.simplebook.domain.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ebookreader.simplebook.domain.model.Book
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookService: BookService
) {
    fun createImportIntent(): Intent {
        return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/epub+zip", "text/plain"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
    }

    suspend fun importFromUris(uris: List<Uri>): List<Book> {
        val booksDir = File(context.filesDir, "books").also { it.mkdirs() }
        val importedBooks = mutableListOf<Book>()

        for (uri in uris) {
            try {
                // Determine file extension
                val fileName = getFileName(uri)
                val extension = fileName.substringAfterLast('.', "").lowercase()
                if (extension !in SUPPORTED_EXTENSIONS) continue

                // Copy to app sandbox
                val sandboxName = "${UUID.randomUUID()}.$extension"
                val sandboxFile = File(booksDir, sandboxName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    sandboxFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: continue

                // Import via BookService
                val book = bookService.importBook(sandboxFile)
                importedBooks.add(book)
            } catch (e: Exception) {
                // Skip failed imports, continue with remaining
            }
        }

        return importedBooks
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown"
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("epub", "txt")
    }
}
