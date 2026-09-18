package com.dropnest.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowDecoration
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import com.dropnest.App
import com.dropnest.core.AppInfo
import com.dropnest.core.randomId
import com.dropnest.di.desktopModule
import com.dropnest.di.engineModule
import com.dropnest.di.uiModule
import com.dropnest.domain.AppSettings
import com.dropnest.domain.LocalServer
import com.dropnest.domain.PlatformServices
import com.dropnest.model.OutgoingItem
import com.dropnest.platform.DesktopPlatformServices
import com.dropnest.platform.expandFiles
import com.dropnest.ui.ShareInbox
import org.koin.core.context.startKoin
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.system.exitProcess

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun main(args: Array<String>) {
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    val singleInstance = runCatching { ServerSocket(47899, 1, InetAddress.getLoopbackAddress()) }.getOrNull()
    if (singleInstance == null) {
        Logger.w { "DropNest is already running" }
        exitProcess(0)
    }

    val koin = startKoin { modules(desktopModule(), engineModule, uiModule) }.koin
    val server = koin.get<LocalServer>()
    val settings = koin.get<AppSettings>()
    val platform = koin.get<PlatformServices>() as DesktopPlatformServices
    val inbox = koin.get<ShareInbox>()

    // Files passed on the command line ("Open with", "Send to" shortcut) land in the staging area.
    inbox.offer(expandFiles(args.map(::File).filter { it.exists() }).map { OutgoingItem.File(randomId(8), it) })

    server.start()
    Runtime.getRuntime().addShutdownHook(Thread { runCatching { server.stop() } })

    application {
        val trayState = rememberTrayState()
        var visible by remember { mutableStateOf(true) }
        val logo = remember { BitmapPainter(useResource("icons/logo.png", ::loadImageBitmap)) }

        platform.notificationSink = { title, body -> trayState.sendNotification(Notification(title, body, Notification.Type.Info)) }

        Tray(
            state = trayState,
            icon = logo,
            tooltip = AppInfo.NAME,
            onAction = { visible = true },
            menu = {
                Item("Open ${AppInfo.NAME}") { visible = true }
                Separator()
                Item("Quit") { server.stop(); exitApplication() }
            },
        )

        val windowState = rememberWindowState(size = DpSize(1100.dp, 740.dp))
        val close = { if (settings.current.minimizeToTray) visible = false else { server.stop(); exitApplication() } }
        Window(
            onCloseRequest = close,
            visible = visible,
            title = AppInfo.NAME,
            icon = logo,
            state = windowState,
            decoration = WindowDecoration.Undecorated(resizerThickness = 6.dp),
        ) {
            App(topBar = { NocturneTitleBar(windowState, logo, close) })
        }
    }
}
