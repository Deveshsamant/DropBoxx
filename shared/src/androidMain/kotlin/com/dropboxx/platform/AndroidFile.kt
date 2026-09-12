package com.dropboxx.platform

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.dropboxx.core.MimeTypes
import com.dropboxx.model.PlatformFile
import okio.Source
import okio.source
import java.io.FileNotFoundException

/** A content:// or file:// URI the user shared or picked. */
class AndroidFile(private val context: Context, val uri: Uri) : PlatformFile {

    override val name: String
    override val size: Long
    override val mimeType: String
    override val previewModel: Any get() = uri

    init {
        var n: String? = null
        var s: Long = -1
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) n = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) s = c.getLong(si)
                }
            }
        }
        if (s < 0) s = runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L }.getOrDefault(-1L)
        if (s < 0) s = runCatching { context.contentResolver.openInputStream(uri)?.use { it.available().toLong() } ?: 0L }.getOrDefault(0L)
        name = n ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        size = s
        mimeType = context.contentResolver.getType(uri) ?: MimeTypes.fromFileName(name)
    }

    override fun open(): Source =
        (context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException(uri.toString())).source()
}
