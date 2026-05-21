package com.ebookreader.simplebook.data.parser

import com.ebookreader.simplebook.domain.model.Chapter
import com.ebookreader.simplebook.domain.model.ChapterType

class ChapterSplitter {
    companion object {
        // 章节标记正则：第X章/节/回/卷，或 Chapter X
        private val CHAPTER_PATTERNS = listOf(
            Regex("^\\s*第[一二三四五六七八九十百千万零\\d]+[章节回卷].*", RegexOption.MULTILINE),
            Regex("^\\s*[Cc]hapter\\s+\\d+.*", RegexOption.MULTILINE),
            Regex("^\\s*CHAPTER\\s+[IVXLCDM]+.*", RegexOption.MULTILINE),
        )
        private const val FALLBACK_CHUNK_SIZE = 10000
    }

    fun split(text: String): List<Chapter> {
        // 1. Try regex chapter markers
        for (pattern in CHAPTER_PATTERNS) {
            val chapters = splitByPattern(text, pattern)
            if (chapters.size >= 2) return chapters
        }

        // 2. Try splitting by double newlines (significant paragraphs)
        val paragraphs = text.split(Regex("\\n{2,}"))
        if (paragraphs.size >= 3) {
            return paragraphs.mapIndexed { index, paragraph ->
                Chapter(
                    index = index,
                    title = "Section ${index + 1}",
                    content = paragraph.trim(),
                    type = ChapterType.TXT_PLAIN
                )
            }
        }

        // 3. Fallback: fixed-size chunks
        return splitBySize(text)
    }

    private fun splitByPattern(text: String, pattern: Regex): List<Chapter> {
        val matches = pattern.findAll(text).toList()
        if (matches.isEmpty()) return emptyList()

        return matches.mapIndexed { index, match ->
            val start = match.range.first
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val content = text.substring(start, end).trim()
            val title = match.value.trim().take(50)

            Chapter(
                index = index,
                title = title,
                content = content,
                type = ChapterType.TXT_PLAIN
            )
        }
    }

    private fun splitBySize(text: String): List<Chapter> {
        if (text.length <= FALLBACK_CHUNK_SIZE) {
            return listOf(
                Chapter(index = 0, title = "Chapter 1", content = text, type = ChapterType.TXT_PLAIN)
            )
        }

        val chapters = mutableListOf<Chapter>()
        var start = 0
        var chapterNum = 1

        while (start < text.length) {
            var end = (start + FALLBACK_CHUNK_SIZE).coerceAtMost(text.length)
            // Try to break at sentence boundary
            if (end < text.length) {
                val lastPeriod = text.lastIndexOfAny(charArrayOf('。', '，', '.', '!', '?', '\n'), end)
                if (lastPeriod > start) end = lastPeriod + 1
            }

            chapters.add(
                Chapter(
                    index = chapters.size,
                    title = "Chapter $chapterNum",
                    content = text.substring(start, end).trim(),
                    type = ChapterType.TXT_PLAIN
                )
            )
            start = end
            chapterNum++
        }

        return chapters
    }
}
