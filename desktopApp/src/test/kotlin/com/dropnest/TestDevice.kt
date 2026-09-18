package com.dropnest

import com.dropnest.domain.AppSettings
import com.dropnest.domain.FilePicker
import com.dropnest.domain.HotspotController
import com.dropnest.domain.HotspotState
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.ReceiveStorage
import com.dropnest.domain.ServerState
import com.dropnest.engine.box.BoxAccessController
import com.dropnest.engine.box.BoxClient
import com.dropnest.engine.chat.ChatServiceImpl
import com.dropnest.engine.box.BoxRepositoryImpl
import com.dropnest.engine.discovery.MulticastDiscovery
import com.dropnest.engine.identity.IdentityManager
import com.dropnest.engine.net.PeerClients
import com.dropnest.engine.server.DropServer
import com.dropnest.engine.store.HistoryStoreImpl
import com.dropnest.engine.store.TrustStoreImpl
import com.dropnest.engine.transfer.ReceiveController
import com.dropnest.engine.transfer.SendController
import com.dropnest.engine.transfer.SessionRegistry
import com.dropnest.engine.transfer.TransferEngineImpl
import com.dropnest.model.DeviceType
import com.dropnest.model.PlatformFile
import com.dropnest.platform.DesktopBoxFileStore
import com.dropnest.platform.DesktopReceiveStorage
import com.russhwolf.settings.PropertiesSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Properties

/** A complete DropNest engine living in a temp directory - a "device" for integration tests. */
class TestDevice(val name: String, root: File) {
    val dir = File(root, name).apply { mkdirs() }
    val receivedDir = File(dir, "received")
    val notifications = mutableListOf<String>()

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val platform = object : PlatformServices {
        override val deviceType = DeviceType.WINDOWS
        override val defaultAlias = name
        override val dataDirectory = dir.absolutePath
        override val defaultSaveDirectory = receivedDir.absolutePath
        override val receiveStorage: ReceiveStorage = DesktopReceiveStorage { receivedDir.absolutePath }
        override val boxFiles = DesktopBoxFileStore()
        override val filePicker = object : FilePicker {
            override val canPickDirectory = false
            override suspend fun pickFiles(): List<PlatformFile> = emptyList()
            override suspend fun pickDirectory(): String? = null
        }
        override val hotspot = object : HotspotController {
            override val state: StateFlow<HotspotState> = MutableStateFlow(HotspotState(supported = false))
            override suspend fun start() = Unit
            override fun stop() = Unit
        }
        override fun notify(title: String, body: String) { synchronized(notifications) { notifications += "$title | $body" } }
        override fun openFile(pathOrUri: String) = Unit
        override fun revealFile(pathOrUri: String) = Unit
        override fun openUrl(url: String) = Unit
        override fun previewModel(pathOrUri: String): Any? = null
        override fun setMulticastEnabled(enabled: Boolean) = Unit
        override fun setKeepAwake(enabled: Boolean) = Unit
        override fun setLaunchAtStartup(enabled: Boolean) = false
        override val supportsTray = false
    }

    val settings = AppSettings(PropertiesSettings(Properties()), name, receivedDir.absolutePath).apply { update { copy(port = 0) } }
    val identity = IdentityManager(dir.absolutePath, settings, DeviceType.WINDOWS, scope)
    val clients = PeerClients()
    val trust = TrustStoreImpl(dir.absolutePath)
    val history = HistoryStoreImpl(dir.absolutePath)
    val discovery = MulticastDiscovery(identity, clients, trust, settings, platform, scope, multicastEnabled = false)
    val registry = SessionRegistry(history, platform, scope)
    val receive = ReceiveController(registry, trust, settings, platform, scope)
    val send = SendController(registry, clients, trust, identity, scope)
    val box = BoxRepositoryImpl(dir.absolutePath, platform)
    val boxAccess = BoxAccessController(box, trust, settings, identity, platform)
    val boxClient = BoxClient(registry, clients, trust, identity, platform, scope, receive.receivedContent)
    val engine = TransferEngineImpl(registry, send, receive, boxClient, boxAccess)
    val chat = ChatServiceImpl(dir.absolutePath, identity, trust, discovery, clients, platform, box, scope)
    val server = DropServer(identity, settings, discovery, receive, boxAccess, chat, scope)

    suspend fun start(): Int {
        server.start()
        // Cold JVM + RSA keygen + Netty boot can take a while on a loaded laptop.
        val running = withTimeout(90_000) { server.state.first { it is ServerState.Running } as ServerState.Running }
        return running.port
    }

    fun stop() {
        server.stop()
        clients.close()
        scope.cancel()
    }
}
