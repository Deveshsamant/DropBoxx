package com.dropboxx.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import co.touchlab.kermit.Logger
import com.dropboxx.domain.BoxFileStore
import com.dropboxx.model.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File

/**
 * Box items must stay readable after the sharing app's one-shot URI grant expires.
 * Documents picked with OpenDocument can be persisted; everything else is copied into private storage.
 */
class AndroidBoxFileStore(private val context: Context) : BoxFileStore {

    private val log = Logger.withTag("BoxFiles")
    private val boxDir = File(context.filesDir, "box").apply { mkdirs() }

    override suspend fun retain(file: PlatformFile, itemId: String): String = withContext(Dispatchers.IO) {
        when (file) {
            is LocalFile -> file.file.absolutePath
            is AndroidFile -> {
                val persisted = runCatching {
                    context.contentResolver.takePersistableUriPermission(file.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    true
                }.getOrDefault(false)
                if (persisted) file.uri.toString() else copyIn(file, itemId)
            }
            else -> copyIn(file, itemId)
        }
    }

    private fun copyIn(file: PlatformFile, itemId: String): String {
        val dir = File(boxDir, itemId).apply { mkdirs() }
        val target = File(dir, file.name.ifBlank { "file" })
        file.open().use { src -> target.sink().buffer().use { it.writeAll(src) } }
        return target.absolutePath
    }

    override fun resolve(locator: String): PlatformFile? = when {
        locator.startsWith("content://") -> {
            val uri = Uri.parse(locator)
            runCatching { AndroidFile(context, uri).takeIf { it.size > 0 && context.contentResolver.openInputStream(uri)?.use { true } == true } }
                .onFailure { log.d { "lost access to $locator: ${it.message}" } }.getOrNull()
        }
        else -> File(locator).takeIf { it.isFile && it.canRead() }?.let { LocalFile(it) }
    }

    override fun release(locator: String) {
        if (locator.startsWith("content://")) {
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(locator), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        } else {
            val f = File(locator)
            if (f.absolutePath.startsWith(boxDir.absolutePath)) f.parentFile?.deleteRecursively()
        }
    }
}
