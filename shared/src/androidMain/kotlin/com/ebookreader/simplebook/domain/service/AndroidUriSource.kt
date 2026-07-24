package com.ebookreader.simplebook.domain.service

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Android [ImportSource] backed by a SAF [Uri]. The display name is resolved via
 * [android.content.ContentResolver] query ([OpenableColumns.DISPLAY_NAME]) with a
 * last-path-segment fallback; the stream is opened through `ContentResolver.openInputStream`.
 */
class AndroidUriSource(
    private val context: Context,
    private val uri: Uri
) : ImportSource {
    override val name: String by lazy { queryDisplayName() ?: "unknown" }

    override fun openInputStream(): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw FileNotFoundException("Cannot open URI: $uri")

    private fun queryDisplayName(): String? {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) {
                return cursor.getString(nameIndex)
            }
        }
        return uri.lastPathSegment
    }
}

/** Convenience: convert a list of SAF [Uri]s into [ImportSource]s. */
fun List<Uri>.toImportSources(context: Context): List<ImportSource> =
    map { AndroidUriSource(context, it) }
