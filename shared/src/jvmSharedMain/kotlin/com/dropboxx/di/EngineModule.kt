package com.dropboxx.di

import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.BoxRepository
import com.dropboxx.domain.DeviceIdentity
import com.dropboxx.domain.DiscoveryService
import com.dropboxx.domain.HistoryStore
import com.dropboxx.domain.LocalServer
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.TransferEngine
import com.dropboxx.domain.TrustStore
import com.dropboxx.engine.box.BoxAccessController
import com.dropboxx.engine.box.BoxClient
import com.dropboxx.engine.box.BoxRepositoryImpl
import com.dropboxx.engine.discovery.MulticastDiscovery
import com.dropboxx.engine.identity.IdentityManager
import com.dropboxx.engine.net.PeerClients
import com.dropboxx.engine.server.DropServer
import com.dropboxx.engine.store.HistoryStoreImpl
import com.dropboxx.engine.store.TrustStoreImpl
import com.dropboxx.engine.transfer.ReceiveController
import com.dropboxx.engine.transfer.SendController
import com.dropboxx.engine.transfer.SessionRegistry
import com.dropboxx.engine.transfer.TransferEngineImpl
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
    single<LocalServer> { DropServer(get(), get(), get(), get(), get(), get()) }
}
