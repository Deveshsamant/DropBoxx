package com.dropnest.platform

import co.touchlab.kermit.Logger
import com.dropnest.domain.AppSettings
import com.dropnest.domain.BoxFileStore
import com.dropnest.domain.FilePicker
import com.dropnest.domain.HotspotController
import com.dropnest.domain.HotspotState
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.ReceiveStorage
import com.dropnest.model.DeviceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.awt.Desktop
import java.io.File
import java.net.InetAddress
import java.net.URI

class DesktopPlatformServices(private val settings: () -> AppSettings) : PlatformServices {

    private val log = Logger.withTag("Desktop")
    private val os = System.getProperty("os.name").lowercase()

    override val deviceType: DeviceType = when {
        os.contains("win") -> DeviceType.WINDOWS
        os.contains("mac") -> DeviceType.MACOS
        else -> DeviceType.LINUX
    }

    override val defaultAlias: String =
        runCatching { InetAddress.getLocalHost().hostName }.getOrNull()?.takeIf { it.isNotBlank() } ?: "My PC"

    override val dataDirectory: String = when (deviceType) {
        DeviceType.WINDOWS -> File(System.getenv("APPDATA") ?: System.getProperty("user.home"), "DropNest").absolutePath
        DeviceType.MACOS -> File(System.getProperty("user.home"), "Library/Application Support/DropNest").absolutePath
        else -> File(System.getProperty("user.home"), ".local/share/dropnest").absolutePath
    }.also { File(it).mkdirs() }

    override val defaultSaveDirectory: String = File(System.getProperty("user.home"), "Downloads/DropNest").absolutePath

    override val receiveStorage: ReceiveStorage = DesktopReceiveStorage { settings().current.saveDirectory }
    override val boxFiles: BoxFileStore = DesktopBoxFileStore()
    override val filePicker: FilePicker = DesktopFilePicker()
    override val hotspot: HotspotController = object : HotspotController {
        override val state: StateFlow<HotspotState> = MutableStateFlow(HotspotState(supported = false))
        override suspend fun start() = Unit
        override fun stop() = Unit
    }

    /** Wired to the tray by the desktop entry point once the window exists. */
    @Volatile var notificationSink: ((String, String) -> Unit)? = null

    override fun notify(title: String, body: String) {
        notificationSink?.invoke(title, body) ?: log.i { "notify: $title - $body" }
    }

    override fun openFile(pathOrUri: String) {
        runCatching { Desktop.getDesktop().open(File(pathOrUri)) }.onFailure { log.w { "open failed: ${it.message}" } }
    }

    override fun revealFile(pathOrUri: String) {
        val file = File(pathOrUri)
        runCatching {
            when (deviceType) {
                DeviceType.WINDOWS -> ProcessBuilder("explorer.exe", "/select,", file.absolutePath).start()
                DeviceType.MACOS -> ProcessBuilder("open", "-R", file.absolutePath).start()
                else -> Desktop.getDesktop().open(file.parentFile)
            }
        }.onFailure { log.w { "reveal failed: ${it.message}" } }
    }

    override fun openUrl(url: String) {
        runCatching { Desktop.getDesktop().browse(URI(url)) }.onFailure { log.w { "browse failed: ${it.message}" } }
    }

    override fun previewModel(pathOrUri: String): Any? = File(pathOrUri).takeIf { it.isFile }

    override fun setMulticastEnabled(enabled: Boolean) = Unit
    override fun setKeepAwake(enabled: Boolean) = Unit

    override fun setLaunchAtStartup(enabled: Boolean): Boolean {
        if (deviceType != DeviceType.WINDOWS) return false
        val startup = File(System.getenv("APPDATA"), "Microsoft/Windows/Start Menu/Programs/Startup/DropNest.lnk")
        return runCatching {
            if (!enabled) { startup.delete(); return true }
            val exe = ProcessHandle.current().info().command().orElse(null) ?: return false
            if (exe.endsWith("java.exe", ignoreCase = true)) { log.w { "Launch at startup needs the packaged app" }; return false }
            val script = "\$s=(New-Object -ComObject WScript.Shell).CreateShortcut('${startup.absolutePath}');\$s.TargetPath='$exe';\$s.WorkingDirectory='${File(exe).parent}';\$s.Save()"
            ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", script).start().waitFor() == 0
        }.getOrDefault(false)
    }

    override val supportsTray: Boolean = deviceType == DeviceType.WINDOWS || deviceType == DeviceType.LINUX
}
