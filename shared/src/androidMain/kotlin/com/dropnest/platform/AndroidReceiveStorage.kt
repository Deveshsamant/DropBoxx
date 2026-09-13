package com.dropnest.platform

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.dropnest.core.MimeTypes
import com.dropnest.domain.ReceiveStorage
import com.dropnest.domain.ReceiveTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Sink
import okio.sink
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Received files go where users expect them: photos into Pictures, videos into Movies, audio into
 * Music and everything else into Download - each in a DropNest sub-folder, via MediaStore on
 * Android 10+ (no storage permission needed) and the public directories below that.
 */
class AndroidReceiveStorage(private val context: Context) : ReceiveStorage {

    override val saveDirectoryDisplay: String = "Download/DropNest (photos in Pictures, videos in Movies)"

    override suspend fun open(fileName: String, mimeType: String, expectedSize: Long): ReceiveTarget = withContext(Dispatchers.IO) {
        val name = fileName.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().ifEmpty { "file" }.take(180)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) mediaStoreTarget(name, mimeType) else legacyTarget(name, mimeType)
    }

    private fun mediaStoreTarget(name: String, mime: String): ReceiveTarget {
        val (collection, dir) = when {
            MimeTypes.isImage(mime) -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_PICTURES
            MimeTypes.isVideo(mime) -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MOVIES
            MimeTypes.isAudio(mime) -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_MUSIC
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI to Environment.DIRECTORY_DOWNLOADS
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (mime == "application/octet-stream") null else mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$dir/DropNest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri: Uri = resolver.insert(collection, values) ?: throw IOException("MediaStore refused to create $name")
        return object : ReceiveTarget {
            @Volatile private var written = 0L
            override val displayPath: String get() = "$dir/DropNest/$name"
            override val bytesWritten: Long get() = written
            override fun sink(): Sink {
                val out = resolver.openOutputStream(uri, if (written > 0) "wa" else "w") ?: throw IOException("Cannot open $uri")
                return CountingSink(out.sink()) { written += it }
            }
            override suspend fun complete(): String {
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                return uri.toString()
            }
            override suspend fun abort() { runCatching { resolver.delete(uri, null, null) } }
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyTarget(name: String, mime: String): ReceiveTarget {
        val dirType = when {
            MimeTypes.isImage(mime) -> Environment.DIRECTORY_PICTURES
            MimeTypes.isVideo(mime) -> Environment.DIRECTORY_MOVIES
            MimeTypes.isAudio(mime) -> Environment.DIRECTORY_MUSIC
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val dir = File(Environment.getExternalStoragePublicDirectory(dirType), "DropNest").apply { mkdirs() }
        var final = File(dir, name)
        var i = 1
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        while (final.exists()) { final = File(dir, "$base ($i)$ext"); i++ }
        val part = File(dir, final.name + ".part")
        return object : ReceiveTarget {
            override val displayPath: String get() = final.absolutePath
            override val bytesWritten: Long get() = if (part.exists()) part.length() else 0L
            override fun sink(): Sink = FileOutputStream(part, true).sink()
            override suspend fun complete(): String {
                if (!part.renameTo(final)) throw IOException("Could not finalise ${final.name}")
                MediaScannerConnection.scanFile(context, arrayOf(final.absolutePath), arrayOf(mime), null)
                return final.absolutePath
            }
            override suspend fun abort() { part.delete() }
        }
    }
}

private class CountingSink(private val delegate: Sink, private val onWrite: (Long) -> Unit) : Sink by delegate {
    override fun write(source: okio.Buffer, byteCount: Long) {
        delegate.write(source, byteCount)
        onWrite(byteCount)
    }
}
