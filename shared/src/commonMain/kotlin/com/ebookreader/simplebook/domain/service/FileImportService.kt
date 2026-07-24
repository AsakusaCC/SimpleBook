package com.ebookreader.simplebook.domain.service

import com.ebookreader.simplebook.domain.model.Book
import com.ebookreader.simplebook.platform.getBooksDir
import java.io.File
import java.util.UUID

/**
 * Cross-platform book import.
 *
 * Platform differences (how to open a stream + derive the file name from a user selection)
 * are hidden behind [ImportSource]; this service only deals with the portable core:
 * copy each source into the app's `books` sandbox (random UUID name to avoid clashes) and
 * hand the sandbox file off to [BookService.importBook] with the original-name hint.
 *
 * - Android sources: [AndroidUriSource] (SAF Uri via ContentResolver).
 * - Desktop sources: [DesktopFileSource] (plain java.io.File from drag-and-drop).
 */
class FileImportService(private val bookService: BookService) {

    suspend fun importFromSources(sources: List<ImportSource>): List<Book> {
        val booksDir = File(getBooksDir()).also { it.mkdirs() }
        val imported = mutableListOf<Book>()

        for (src in sources) {
            try {
                val extension = src.name.substringAfterLast('.', "").lowercase()
                if (extension !in SUPPORTED_EXTENSIONS) continue
                val originalName = src.name.substringBeforeLast('.')

                val sandboxFile = File(booksDir, "${UUID.randomUUID()}.$extension")
                src.openInputStream().use { input ->
                    sandboxFile.outputStream().use { output -> input.copyTo(output) }
                }

                imported.add(bookService.importBook(sandboxFile, originalName))
            } catch (e: Exception) {
                // Skip failed imports, continue with remaining files.
            }
        }

        return imported
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("epub", "txt")
    }
}
