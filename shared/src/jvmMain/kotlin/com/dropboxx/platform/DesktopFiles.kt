package com.dropboxx.platform

import com.dropboxx.domain.BoxFileStore
import com.dropboxx.domain.FilePicker
import com.dropboxx.domain.ReceiveStorage
import com.dropboxx.domain.ReceiveTarget
import com.dropboxx.model.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okio.Sink
import okio.sink
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser

typealias DesktopFile = LocalFile

/** Desktop box items simply reference the original file; nothing is copied. */
class DesktopBoxFileStore : BoxFileStore {
    override suspend fun retain(file: PlatformFile, itemId: String): String =
        (file as? LocalFile)?.file?.absolutePath ?: throw IllegalArgumentException("unsupported file source")
    override fun resolve(locator: String): PlatformFile? = File(locator).takeIf { it.isFile && it.canRead() }?.let { LocalFile(it) }
    override fun release(locator: String) = Unit
}

/** Expands dropped folders into their files so a folder drop just works. */
fun expandFiles(files: Collection<File>): List<PlatformFile> = files.flatMap { f ->
    when {
        f.isDirectory -> f.walkTopDown().filter { it.isFile }.map { DesktopFile(it) }.toList()
        f.isFile -> listOf(DesktopFile(f))
        else -> emptyList()
    }
}

class DesktopFilePicker : FilePicker {
    override val canPickDirectory = true

    override suspend fun pickFiles(): List<PlatformFile> = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, "Choose files to send", FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
        expandFiles(dialog.files.toList())
    }

    override suspend fun pickDirectory(): String? = withContext(Dispatchers.Swing) {
        val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose where received files are saved" }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
    }
}

/** Writes to `<name>.part` and renames on completion; collisions get " (n)" suffixes. */
class DesktopReceiveStorage(private val saveDirectory: () -> String) : ReceiveStorage {

    override val saveDirectoryDisplay: String get() = saveDirectory()

    private val lock = Any()

    override suspend fun open(fileName: String, mimeType: String, expectedSize: Long): ReceiveTarget = withContext(Dispatchers.IO) {
        val dir = File(saveDirectory()).apply { mkdirs() }
        // Reserve the name under a lock and create the .part immediately so two concurrent
        // files with the same name cannot pick the same slot.
        val (final, part) = synchronized(lock) {
            val f = uniqueFile(dir, sanitize(fileName))
            f to File(dir, f.name + ".part").apply { delete(); createNewFile() }
        }
        object : ReceiveTarget {
            override val displayPath: String get() = final.absolutePath
            override val bytesWritten: Long get() = if (part.exists()) part.length() else 0L
            override fun sink(): Sink = java.io.FileOutputStream(part, true).sink()
            override suspend fun complete(): String = withContext(Dispatchers.IO) {
                // Only finished files count as collisions here; our own .part must not.
                val target = uniqueFile(dir, final.name, considerParts = false)
                if (!part.renameTo(target)) throw java.io.IOException("Could not move ${part.name} into place")
                target.absolutePath
            }
            override suspend fun abort() { withContext(Dispatchers.IO) { part.delete() } }
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("""[\\/:*?"<>|\p{Cntrl}]"""), "_").trim().ifEmpty { "file" }.take(180)

    /** First of `name`, `base (1).ext`, `base (2).ext`... that is free. */
    private fun uniqueFile(dir: File, name: String, considerParts: Boolean = true): File {
        fun taken(f: File) = f.exists() || (considerParts && File(dir, f.name + ".part").exists())
        var candidate = File(dir, name)
        if (!taken(candidate)) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var i = 1
        while (taken(candidate)) {
            candidate = File(dir, "$base ($i)$ext"); i++
        }
        return candidate
    }
}
