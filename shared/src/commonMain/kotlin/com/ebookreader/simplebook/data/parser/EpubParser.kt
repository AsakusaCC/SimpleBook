package com.ebookreader.simplebook.data.parser

import com.ebookreader.simplebook.domain.model.TocEntry
import com.ebookreader.simplebook.domain.model.TableOfContents
import com.ebookreader.simplebook.platform.getBooksDir
import nl.siegmann.epublib.domain.Book as EpubBook
import nl.siegmann.epublib.epub.EpubReader
import java.io.File
import java.io.FileInputStream

class EpubParser constructor(
) {
    private val epubReader = EpubReader()

    /**
     * 解析 EPUB。返回 null（不抛异常）的情况：文件损坏/截断/非合法 zip，或解析出的 spine
     * 为空（如 0 字节文件 —— epublib 会返回空 Book，无正文可读）。调用方据此提示用户。
     */
    fun parse(file: File): EpubParseResult? {
        return try {
            val epubBook = epubReader.readEpub(FileInputStream(file))
            val chapterCount = epubBook.spine.spineReferences.size
            if (chapterCount == 0) {
                println("EpubParser: ${file.name} has no readable chapters (empty/invalid)")
                return null
            }

            val title = epubBook.metadata.titles.firstOrNull() ?: file.nameWithoutExtension
            val author = epubBook.metadata.authors.firstOrNull()?.let { "${it.firstname} ${it.lastname}".trim() } ?: ""

            EpubParseResult(
                title = title,
                author = author,
                coverPath = extractCoverImage(epubBook)?.absolutePath,
                tableOfContents = buildTableOfContents(epubBook),
                chapterCount = chapterCount,
                epubBook = epubBook
            )
        } catch (e: Exception) {
            println("EpubParser: failed to parse ${file.name}: ${e.message}")
            null
        }
    }

    fun getChapterContent(epubBook: EpubBook, chapterIndex: Int): String? {
        val spineReferences = epubBook.spine.spineReferences
        if (chapterIndex < 0 || chapterIndex >= spineReferences.size) return null

        val resource = spineReferences[chapterIndex].resource
        return String(resource.data, Charsets.UTF_8)
    }

    private fun extractCoverImage(epubBook: EpubBook): File? {
        // 1. Standard: epublib's built-in cover detection
        epubBook.coverImage?.let { return saveCoverImage(it) }

        // 2. Fallback: find <meta name="cover" content="xxx"/> in OPF metadata,
        //    then look up the corresponding image resource by ID
        val coverMetaValue = epubBook.metadata.otherProperties
            .entries.firstOrNull { it.key.localPart == "cover" }?.value
        if (coverMetaValue != null) {
            epubBook.resources.getByIdOrHref(coverMetaValue)?.let { resource ->
                if (resource.mediaType?.name?.startsWith("image/") == true) {
                    return saveCoverImage(resource)
                }
            }
        }

        // 3. Fallback: find the largest image resource (cover is usually the biggest)
        val largestImage = epubBook.resources.all
            .filter { it.mediaType?.name?.startsWith("image/") == true }
            .maxByOrNull { it.size }
        if (largestImage != null) {
            return saveCoverImage(largestImage)
        }

        return null
    }

    private fun saveCoverImage(resource: nl.siegmann.epublib.domain.Resource): File? {
        return try {
            val coversDir = File(getBooksDir(), "covers").also { it.mkdirs() }
            val coverFile = File(coversDir, "${System.currentTimeMillis()}.jpg")
            coverFile.outputStream().use { output ->
                resource.inputStream.use { input ->
                    input.copyTo(output)
                }
            }
            coverFile
        } catch (e: Exception) {
            null
        }
    }

    private fun buildTableOfContents(epubBook: EpubBook): TableOfContents {
        val entries = epubBook.tableOfContents.tocReferences.map { ref ->
            buildTocEntry(epubBook, ref)
        }
        return TableOfContents(entries)
    }

    private fun buildTocEntry(epubBook: EpubBook, ref: nl.siegmann.epublib.domain.TOCReference): TocEntry {
        // Find the spine index for this resource
        val spineIndex = findSpineIndex(epubBook, ref.resource.id)

        return TocEntry(
            title = ref.title ?: "",
            href = ref.resource.href ?: "",
            chapterIndex = spineIndex,
            children = ref.children.map { buildTocEntry(epubBook, it) }
        )
    }

    private fun findSpineIndex(epubBook: EpubBook, resourceId: String?): Int {
        if (resourceId == null) return -1
        epubBook.spine.spineReferences.forEachIndexed { index, ref ->
            if (ref.resource.id == resourceId) return index
        }
        return -1
    }
}
