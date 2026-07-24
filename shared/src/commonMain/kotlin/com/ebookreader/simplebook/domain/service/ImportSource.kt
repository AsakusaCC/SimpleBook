package com.ebookreader.simplebook.domain.service

import java.io.File
import java.io.InputStream

/**
 * Cross-platform abstraction over a book-import source — a file the user wants to import.
 *
 * The platform-specific part of import (how to open a stream + how to derive the file name)
 * is encapsulated here, so [FileImportService.importFromSources] can run unchanged on both
 * Android and Desktop:
 *
 * - Android: [AndroidUriSource] (androidMain) wraps a SAF `android.net.Uri` and reads via
 *   `ContentResolver`.
 * - Desktop / JVM: [DesktopFileSource] wraps a plain [java.io.File] obtained from a
 *   drag-and-drop operation.
 *
 * [DesktopFileSource] only depends on `java.io.*` (available in this JVM-only KMP setup's
 * commonMain), so it lives here rather than in desktopMain and can be constructed directly
 * from commonMain code (e.g. BookListScreen's drop callback).
 */
interface ImportSource {
    /** File name including extension — used to derive format + the original-title hint. */
    val name: String

    fun openInputStream(): InputStream
}

/**
 * [ImportSource] backed by a [java.io.File]. JVM-portable, so defined in commonMain; in
 * practice only constructed on desktop from drag-and-dropped files.
 */
class DesktopFileSource(private val file: File) : ImportSource {
    override val name: String = file.name
    override fun openInputStream(): InputStream = file.inputStream()
}
