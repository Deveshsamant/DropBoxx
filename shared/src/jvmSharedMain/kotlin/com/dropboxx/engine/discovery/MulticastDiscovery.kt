package com.dropboxx.engine.discovery

import co.touchlab.kermit.Logger
import com.dropboxx.core.AppInfo
import com.dropboxx.core.nowMillis
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.DiscoveryService
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.TrustStore
import com.dropboxx.engine.identity.IdentityManager
import com.dropboxx.engine.net.NetworkUtils
import com.dropboxx.engine.net.PeerClients
import com.dropboxx.engine.net.ProtocolJson
import com.dropboxx.model.Api
import com.dropboxx.model.DeviceInfo
import com.dropboxx.model.MulticastMessage
import com.dropboxx.model.Peer
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket

/**
 * Three-tier discovery, most reliable first:
 *  1. UDP multicast beacons (instant, works on hotspots without a router)
 *  2. Unicast HTTPS register replies to beacons (survives routers that swallow multicast one way)
 *  3. Subnet scan of the default port (manual "Refresh" fallback for hostile networks)
 */
class MulticastDiscovery(
    private val identity: IdentityManager,
    private val clients: PeerClients,
    private val trustStore: TrustStore,
    private val settings: AppSettings,
    private val platform: PlatformServices,
    appScope: CoroutineScope,
) : DiscoveryService {

    private val log = Logger.withTag("Discovery")
    private val group: InetAddress = InetAddress.getByName(AppInfo.MULTICAST_GROUP)

    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    override val peers: StateFlow<List<Peer>> = combine(_peers, trustStore.devices) { map, trusted ->
        val trustedIds = trusted.map { it.id }.toSet()
        map.values.map { it.copy(trusted = it.id in trustedIds) }
            .sortedWith(compareByDescending<Peer> { it.trusted }.thenBy { it.info.alias.lowercase() })
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> get() = _scanning

    private var scope: CoroutineScope? = null
    private var socket: MulticastSocket? = null

    private val me: DeviceInfo get() = identity.info.value

    @Synchronized
    override fun start() {
        if (scope != null) return
        val s = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = s
        platform.setMulticastEnabled(true)
        s.launch { listenLoop() }
        s.launch {
            while (isActive) {
                sendBeacon(announce = true)
                delay(AppInfo.ANNOUNCE_INTERVAL_MILLIS)
            }
        }
        s.launch {
            while (isActive) {
                delay(5_000)
                val cutoff = nowMillis() - AppInfo.PEER_TTL_MILLIS
                _peers.value = _peers.value.filterValues { it.lastSeenMillis >= cutoff }
            }
        }
    }

    @Synchronized
    override fun stop() {
        scope?.cancel()
        scope = null
        runCatching { socket?.close() }
        socket = null
        platform.setMulticastEnabled(false)
    }

    override fun announce() {
        scope?.launch { sendBeacon(announce = true) }
    }

    override fun forget(peerId: String) {
        _peers.value = _peers.value - peerId
    }

    override fun upsert(peer: Peer) {
        if (peer.id == me.id) return
        _peers.value = _peers.value + (peer.id to peer)
    }

    private fun openSocket(): MulticastSocket {
        val sock = MulticastSocket(AppInfo.MULTICAST_PORT).apply { reuseAddress = true }
        var joined = 0
        for (iface in NetworkUtils.multicastCapableInterfaces()) {
            runCatching { sock.joinGroup(InetSocketAddress(group, AppInfo.MULTICAST_PORT), iface); joined++ }
                .onFailure { log.d { "join failed on ${iface.name}: ${it.message}" } }
        }
        if (joined == 0) runCatching { @Suppress("DEPRECATION") sock.joinGroup(group) }
        log.i { "Multicast listening on ${AppInfo.MULTICAST_GROUP}:${AppInfo.MULTICAST_PORT} ($joined interfaces)" }
        return sock
    }

    private suspend fun listenLoop() {
        val buffer = ByteArray(8 * 1024)
        while (scope?.isActive == true) {
            val sock = socket ?: runCatching { openSocket() }.getOrElse {
                log.w(it) { "Multicast socket unavailable, retrying" }
                delay(3_000)
                continue
            }.also { socket = it }
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                sock.receive(packet)
                val from = packet.address?.hostAddress ?: continue
                handlePacket(String(packet.data, 0, packet.length, Charsets.UTF_8), from)
            } catch (e: Exception) {
                if (scope?.isActive != true) return
                log.d { "receive error: ${e.message}" }
                runCatching { sock.close() }
                socket = null
                delay(1_000)
            }
        }
    }

    private fun handlePacket(text: String, from: String) {
        val msg = runCatching { ProtocolJson.decodeFromString(MulticastMessage.serializer(), text) }.getOrNull() ?: return
        if (msg.info.id == me.id) return
        upsert(Peer(msg.info, from, nowMillis()))
        if (msg.announce) scope?.launch { register(msg.info, from) }
    }

    /** Tell [target] about us over unicast so it does not have to wait for our next beacon. */
    private suspend fun register(target: DeviceInfo, address: String) {
        runCatching {
            val client = clients.forFingerprint(target.fingerprint)
            val response = client.post("https://$address:${target.port}${Api.REGISTER}") {
                contentType(ContentType.Application.Json)
                setBody(me)
            }
            if (response.status.isSuccess()) {
                val info: DeviceInfo = response.body()
                upsert(Peer(info, address, nowMillis()))
            }
        }.onFailure { log.d { "register with ${target.alias}@$address failed: ${it.message}" } }
    }

    private fun sendBeacon(announce: Boolean) {
        val payload = ProtocolJson.encodeToString(MulticastMessage.serializer(), MulticastMessage(me, announce)).toByteArray()
        val interfaces = NetworkUtils.multicastCapableInterfaces()
        if (interfaces.isEmpty()) return
        for (iface in interfaces) {
            runCatching {
                MulticastSocket().use { s ->
                    s.networkInterface = iface
                    s.timeToLive = 4
                    s.send(DatagramPacket(payload, payload.size, group, AppInfo.MULTICAST_PORT))
                }
            }.onFailure { log.d { "beacon on ${iface.name} failed: ${it.message}" } }
        }
    }

    override suspend fun scanSubnet() {
        if (_scanning.value) return
        _scanning.value = true
        try {
            val ports = setOf(AppInfo.DEFAULT_PORT, settings.current.port, me.port)
            val hosts = NetworkUtils.localIpv4().flatMap { NetworkUtils.subnetHosts(it.address) }.distinct()
            log.i { "Scanning ${hosts.size} hosts on ports $ports" }
            val gate = Semaphore(48)
            coroutineScope {
                for (host in hosts) for (port in ports) {
                    launch(Dispatchers.IO) { gate.withPermit { probe(host, port) } }
                }
            }
        } finally {
            _scanning.value = false
        }
    }

    override suspend fun addManual(address: String, port: Int): Peer? {
        // A single host the user typed deserves patience: cold TLS stacks and sleepy phones can miss the first probe.
        var found: DeviceInfo? = null
        repeat(3) { attempt ->
            found = withTimeoutOrNull(4_000) {
                runCatching {
                    val r = clients.probe.get("https://$address:$port${Api.INFO}")
                    if (r.status.isSuccess()) r.body<DeviceInfo>() else null
                }.getOrNull()
            }
            if (found != null) return@repeat
            delay(500L * (attempt + 1))
        }
        val info = found ?: return null
        if (info.id == me.id) return null
        val peer = Peer(info, address, nowMillis())
        upsert(peer)
        register(info, address)
        return peer
    }

    private suspend fun probe(host: String, port: Int) {
        val info = withTimeoutOrNull(2_500) {
            runCatching {
                val r = clients.probe.get("https://$host:$port${Api.INFO}")
                if (r.status.isSuccess()) r.body<DeviceInfo>() else null
            }.getOrNull()
        } ?: return
        if (info.id == me.id) return
        upsert(Peer(info, host, nowMillis()))
        register(info, host)
    }
}
