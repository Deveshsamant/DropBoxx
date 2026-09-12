package com.dropboxx.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import co.touchlab.kermit.Logger
import com.dropboxx.App
import com.dropboxx.core.AppInfo
import com.dropboxx.core.randomId
import com.dropboxx.di.desktopModule
import com.dropboxx.di.engineModule
import com.dropboxx.di.uiModule
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.LocalServer
import com.dropboxx.domain.PlatformServices
import com.dropboxx.model.OutgoingItem
import com.dropboxx.platform.DesktopPlatformServices
import com.dropboxx.platform.expandFiles
import com.dropboxx.ui.ShareInbox
import com.dropboxx.ui.theme.DropBoxxLogo
import org.koin.core.context.startKoin
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    System.setProperty("sun.java2d.uiScale.enabled", "true")
    val singleInstance = runCatching { ServerSocket(47899, 1, InetAddress.getLoopbackAddress()) }.getOrNull()
    if (singleInstance == null) {
        Logger.w { "DropBoxx is already running" }
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
        val logo = rememberVectorPainter(DropBoxxLogo)

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

        Window(
            onCloseRequest = { if (settings.current.minimizeToTray) visible = false else { server.stop(); exitApplication() } },
            visible = visible,
            title = AppInfo.NAME,
            icon = logo,
            state = rememberWindowState(size = DpSize(1100.dp, 740.dp)),
        ) {
            App()
        }
    }
}
