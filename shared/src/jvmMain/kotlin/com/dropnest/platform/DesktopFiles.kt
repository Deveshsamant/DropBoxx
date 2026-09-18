package com.dropnest.platform

import com.dropnest.domain.BoxFileStore
import com.dropnest.domain.FilePicker
import com.dropnest.domain.ReceiveStorage
import com.dropnest.domain.ReceiveTarget
import com.dropnest.model.PlatformFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import okio.Sink
import okio.sink
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Base64
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

    override suspend fun pickFiles(): List<PlatformFile> {
        if (isWindows) WindowsDialogs.pickFiles()?.let { return expandFiles(it) }
        return withContext(Dispatchers.Swing) {
            val dialog = FileDialog(null as Frame?, "Add to your box", FileDialog.LOAD).apply { isMultipleMode = true; isVisible = true }
            expandFiles(dialog.files.toList())
        }
    }

    override suspend fun pickDirectory(): String? {
        if (isWindows) WindowsDialogs.pickFolder()?.let { return it.firstOrNull() }
        return withContext(Dispatchers.Swing) {
            val chooser = JFileChooser().apply { fileSelectionMode = JFileChooser.DIRECTORIES_ONLY; dialogTitle = "Choose where received files are saved" }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.absolutePath else null
        }
    }
}

private val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")

/**
 * The JDK only knows the legacy "Look in:" dialog on Windows. The modern Explorer-style picker
 * (IFileDialog) is reached through a tiny PowerShell helper: WinForms for files, the COM
 * interface with FOS_PICKFOLDERS for folders. Returns null when the helper fails, so callers fall
 * back to the AWT dialogs; an empty list means the user cancelled.
 */
private object WindowsDialogs {
    private const val FILES = """
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
Add-Type -AssemblyName System.Windows.Forms
${'$'}d = New-Object System.Windows.Forms.OpenFileDialog
${'$'}d.Multiselect = ${'$'}true; ${'$'}d.Title = 'Add to your box'; ${'$'}d.Filter = 'All files (*.*)|*.*'
if (${'$'}d.ShowDialog() -eq 'OK') { ${'$'}d.FileNames | ForEach-Object { [Console]::Out.WriteLine(${'$'}_) } }
"""

    private const val FOLDER = """
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
${'$'}code = @"
using System; using System.Runtime.InteropServices;
[ComImport, Guid("DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7")] class FileOpenDialogRCW {}
[ComImport, Guid("42f85136-db7e-439c-85f1-e4075d135fc8"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IFileDialog { [PreserveSig] uint Show(IntPtr parent); void SetFileTypes(uint c, IntPtr f); void SetFileTypeIndex(uint i); void GetFileTypeIndex(out uint i); void Advise(IntPtr e, out uint c); void Unadvise(uint c); void SetOptions(uint o); void GetOptions(out uint o); void SetDefaultFolder(IntPtr p); void SetFolder(IntPtr p); void GetFolder(out IntPtr p); void GetCurrentSelection(out IntPtr p); void SetFileName([MarshalAs(UnmanagedType.LPWStr)] string n); void GetFileName([MarshalAs(UnmanagedType.LPWStr)] out string n); void SetTitle([MarshalAs(UnmanagedType.LPWStr)] string t); void SetOkButtonLabel([MarshalAs(UnmanagedType.LPWStr)] string t); void SetFileNameLabel([MarshalAs(UnmanagedType.LPWStr)] string t); void GetResult(out IShellItem r); void AddPlace(IntPtr p, int f); void SetDefaultExtension([MarshalAs(UnmanagedType.LPWStr)] string e); void Close(int hr); void SetClientGuid(ref Guid g); void ClearClientData(); void SetFilter(IntPtr f); }
[ComImport, Guid("43826d1e-e718-42ee-bc55-a1e261c37bfe"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
interface IShellItem { void BindToHandler(IntPtr p, ref Guid b, ref Guid r, out IntPtr o); void GetParent(out IShellItem p); void GetDisplayName(uint n, [MarshalAs(UnmanagedType.LPWStr)] out string s); void GetAttributes(uint m, out uint a); void Compare(IShellItem o, uint h, out int r); }
public static class Picker { public static string Folder(string title) { var d = (IFileDialog)new FileOpenDialogRCW(); d.SetOptions(0x20 | 0x40); d.SetTitle(title); if (d.Show(IntPtr.Zero) != 0) return null; IShellItem r; d.GetResult(out r); string s; r.GetDisplayName(0x80058000, out s); return s; } }
"@
Add-Type -TypeDefinition ${'$'}code
${'$'}r = [Picker]::Folder('Choose where received files are saved')
if (${'$'}r) { [Console]::Out.WriteLine(${'$'}r) }
"""

    suspend fun pickFiles(): List<File>? = run(FILES)?.map(::File)?.filter { it.exists() }
    suspend fun pickFolder(): List<String>? = run(FOLDER)

    private suspend fun run(script: String): List<String>? = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
            val process = ProcessBuilder(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-STA", "-ExecutionPolicy", "Bypass", "-EncodedCommand", encoded,
            ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val lines = process.inputStream.bufferedReader(Charsets.UTF_8).readLines()
            if (process.waitFor() == 0) lines.map { it.trim() }.filter { it.isNotEmpty() } else null
        }.getOrNull()
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
