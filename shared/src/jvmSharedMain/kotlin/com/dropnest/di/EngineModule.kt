package com.dropnest.di

import com.dropnest.domain.AppSettings
import com.dropnest.domain.BluetoothControl
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.ChatService
import com.dropnest.engine.bt.BluetoothPeerService
import com.dropnest.engine.bt.BluetoothTransport
import com.dropnest.engine.chat.ChatServiceImpl
import com.dropnest.domain.DeviceIdentity
import com.dropnest.domain.DiscoveryService
import com.dropnest.domain.HistoryStore
import com.dropnest.domain.LocalServer
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.TransferEngine
import com.dropnest.domain.TrustStore
import com.dropnest.engine.box.BoxAccessController
import com.dropnest.engine.box.BoxClient
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
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module

/** Everything that is identical on Android and desktop. Platform modules provide [PlatformServices] and [Settings]. */
val engineModule: Module = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { AppSettings(get(), get<PlatformServices>().defaultAlias, get<PlatformServices>().defaultSaveDirectory) }
    single { IdentityManager(get<PlatformServices>().dataDirectory, get(), get<PlatformServices>().deviceType, get()) } bind DeviceIdentity::class
    single { PeerClients() }
    single<TrustStore> { TrustStoreImpl(get<PlatformServices>().dataDirectory) }
    single<HistoryStore> { HistoryStoreImpl(get<PlatformServices>().dataDirectory) }
    single<DiscoveryService> { MulticastDiscovery(get(), get(), get(), get(), get(), get()) }
    single { SessionRegistry(get(), get(), get()) }
    single { ReceiveController(get(), get(), get(), get(), get()) }
    single { SendController(get(), get(), get(), get(), get()) }
    single<BoxRepository> { BoxRepositoryImpl(get<PlatformServices>().dataDirectory, get()) }
    single { BoxAccessController(get(), get(), get(), get(), get()) }
    single { BoxClient(get(), get(), get(), get(), get(), get(), get<ReceiveController>().receivedContent) }
    single<TransferEngine> { TransferEngineImpl(get(), get(), get(), get(), get()) }
    single { ChatServiceImpl(get<PlatformServices>().dataDirectory, get(), get(), get(), get(), get(), get(), get()) } bind ChatService::class
    single {
        BluetoothPeerService(getOrNull<BluetoothTransport>(), get(), get(), get(), get(), get(), get()).also { bt ->
            bt.chat = get<ChatServiceImpl>()
            get<ChatServiceImpl>().bluetooth = bt
            get<BoxClient>().bluetooth = bt
        }
    } bind BluetoothControl::class
    single<LocalServer> { DropServer(get(), get(), get(), get(), get(), get(), get()) }
}
