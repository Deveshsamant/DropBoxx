package com.dropnest.engine.bt

import co.touchlab.kermit.Logger
import com.dropnest.core.nowMillis
import com.dropnest.domain.AppSettings
import com.dropnest.domain.BluetoothControl
import com.dropnest.domain.BrowseOutcome
import com.dropnest.domain.DeviceIdentity
import com.dropnest.domain.DiscoveryService
import com.dropnest.domain.TrustStore
import com.dropnest.engine.box.BoxAccessController
import com.dropnest.engine.box.ListResult
import com.dropnest.engine.chat.ChatReceiveResult
import com.dropnest.engine.chat.ChatServiceImpl
import com.dropnest.model.BoxEntry
import com.dropnest.model.BoxListRequest
import com.dropnest.model.ChatAck
import com.dropnest.model.ChatEnvelope
import com.dropnest.model.Peer
import com.dropnest.model.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/** Outcome of pushing a chat envelope over Bluetooth. */
sealed interface BtChatOutcome {
    data class Ok(val ack: ChatAck) : BtChatOutcome
    data object Denied : BtChatOutcome
    data object Unreachable : BtChatOutcome
}

/**
 * Bluetooth as the secondary road: same requests as the HTTPS API, one RFCOMM connection per
 * request. Discovery-wise it keeps probing paired/nearby devices for the DropNest service and
 * feeds anything that answers into [DiscoveryService] as a [Transport.BLUETOOTH] peer; the
 * discovery merge keeps a fresh Wi-Fi route in front of it.
 */
class BluetoothPeerService(
    private val transport: BluetoothTransport?,
    private val identity: DeviceIdentity,
    private val discovery: DiscoveryService,
    private val trustStore: TrustStore,
    private val boxAccess: BoxAccessController,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) : BluetoothControl {
    /** Set by the engine module after both exist (chat needs this service and vice versa). */
    lateinit var chat: ChatServiceImpl

    private val log = Logger.withTag("Bluetooth")
    override val supported: Boolean get() = transport?.supported == true
    private val _active = MutableStateFlow(false)
    /** True while the feature is on and the adapter is usable. */
    override val active: StateFlow<Boolean> get() = _active
    val ready: StateFlow<Boolean> get() = transport?.ready ?: MutableStateFlow(false)

    private var loop: Job? = null
    private val failedUntil = ConcurrentHashMap<String, Long>()

    init {
        scope.launch {
            combine(settings.state.map { it.bluetooth }, ready) { on, ok -> on && ok }.collect { run -> if (run) start() else stop() }
        }
    }

    /** User switched Bluetooth on: ask for permissions, persist, start. */
    override suspend fun enable(): Boolean {
        val t = transport ?: return false
        if (!t.enable()) return false
        settings.update { copy(bluetooth = true) }
        return true
    }

    override fun disable() = settings.update { copy(bluetooth = false) }

    private fun start() {
        val t = transport ?: return
        if (loop != null) return
        _active.value = true
        t.listen { socket -> scope.launch(Dispatchers.IO) { serve(socket) } }
        loop = scope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { probeAll(t) }.onFailure { log.w { "scan failed: ${it.message}" } }
                delay(SCAN_INTERVAL_MILLIS)
            }
        }
        log.i { "Bluetooth transport on" }
    }

    private fun stop() {
        loop?.cancel(); loop = null
        transport?.stopListening()
        _active.value = false
        // Drop peers that were only reachable over Bluetooth.
        discovery.peers.value.filter { it.viaBluetooth }.forEach { discovery.forget(it.id) }
    }

    // ---- discovery: who around here runs DropNest? ----

    private suspend fun probeAll(t: BluetoothTransport) {
        val now = nowMillis()
        val wifiFresh = discovery.peers.value.filter { it.transport == Transport.WIFI && it.lastSeenMillis >= now - 30_000 }.mapNotNull { it.bluetoothAddress }.toSet()
        for (device in t.candidates()) {
            if (device.address in wifiFresh) continue                         // already reachable the fast way
            if ((failedUntil[device.address] ?: 0L) > now) continue
            val info = hello(t, device.address)
            if (info == null) { failedUntil[device.address] = now + BACKOFF_MILLIS; continue }
            failedUntil.remove(device.address)
            discovery.upsert(Peer(info, device.address, nowMillis(), trustStore.find(info.id) != null, Transport.BLUETOOTH, device.address))
        }
    }

    private suspend fun hello(t: BluetoothTransport, address: String) = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
        withContext(Dispatchers.IO) {
            val s = t.connect(address) ?: return@withContext null
            s.use {
                BtFrames.Writer(it.output).message(BtMsg(BtMsg.HELLO, hello = identity.info.value))
                BtFrames.Reader(it.input).message()?.hello
            }
        }
    }

    // ---- server side ----

    private fun serve(socket: BtSocket) {
        socket.use { s ->
            val reader = BtFrames.Reader(s.input)
            val writer = BtFrames.Writer(s.output)
            val msg = runCatching { reader.message() }.getOrNull() ?: return
            val remote = "bt:" + s.remoteAddress
            when (msg.type) {
                BtMsg.HELLO -> {
                    msg.hello?.let { discovery.upsert(Peer(it, s.remoteAddress, nowMillis(), trustStore.find(it.id) != null, Transport.BLUETOOTH, s.remoteAddress)) }
                    writer.message(BtMsg(BtMsg.HELLO, hello = identity.info.value))
                }
                BtMsg.CHAT -> {
                    val env = msg.chat ?: return
                    discovery.upsert(Peer(env.from, s.remoteAddress, nowMillis(), trustStore.find(env.from.id) != null, Transport.BLUETOOTH, s.remoteAddress))
                    writer.message(when (val r = runBlocking { chat.receive(env, remote) }) {
                        is ChatReceiveResult.Ok -> BtMsg(BtMsg.CHAT_ACK, chatAck = r.ack)
                        ChatReceiveResult.Denied -> BtMsg.error("denied")
                        ChatReceiveResult.PinRequired -> BtMsg.error("pin")
                        ChatReceiveResult.Busy -> BtMsg.error("busy")
                    })
                }
                BtMsg.LIST -> {
                    val req = msg.list ?: return
                    discovery.upsert(Peer(req.info, s.remoteAddress, nowMillis(), trustStore.find(req.info.id) != null, Transport.BLUETOOTH, s.remoteAddress))
                    writer.message(when (val r = runBlocking { boxAccess.list(req, remote) }) {
                        is ListResult.Ok -> BtMsg(BtMsg.LIST_RES, listRes = r.response)
                        ListResult.Denied -> BtMsg.error("denied")
                        ListResult.PinRequired -> BtMsg.error("pin")
                        ListResult.Busy -> BtMsg.error("busy")
                    })
                }
                BtMsg.GET -> {
                    val get = msg.get ?: return
                    val device = boxAccess.authorize(get.token)
                    if (device == null || !boxAccess.mayDownload(get.itemId, device)) { writer.message(BtMsg.error("denied")); return }
                    val file = boxAccess.openItem(get.itemId) ?: run { writer.message(BtMsg.error("notfound")); return }
                    val start = get.offset.coerceIn(0, file.size)
                    writer.message(BtMsg(BtMsg.GET_RES, getRes = BtGetRes(file.size, start)))
                    file.open().buffer().use { src ->
                        if (start > 0) src.skip(start)
                        val chunk = ByteArray(BtFrames.CHUNK)
                        var sent = start
                        while (sent < file.size) {
                            val n = src.read(chunk, 0, minOf(chunk.size.toLong(), file.size - sent).toInt())
                            if (n <= 0) break
                            writer.data(chunk, n); sent += n
                        }
                    }
                    writer.end()
                }
            }
        }
    }

    // ---- client side ----

    private suspend fun <T> withPeer(peer: Peer, block: (BtFrames.Reader, BtFrames.Writer) -> T): T? {
        val t = transport ?: return null
        val address = peer.bluetoothAddress ?: peer.address
        return withContext(Dispatchers.IO) {
            val s = withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) { t.connect(address) } ?: return@withContext null
            try { s.use { block(BtFrames.Reader(it.input), BtFrames.Writer(it.output)) } }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { log.w { "bt request to ${peer.info.alias} failed: ${e.message}" }; null }
        }
    }

    suspend fun browse(peer: Peer, pin: String?, visitToken: String?): BrowseOutcome {
        val res = withPeer(peer) { r, w ->
            w.message(BtMsg(BtMsg.LIST, list = BoxListRequest(identity.info.value, trustStore.outgoingToken(peer.id), pin, visitToken)))
            r.message()
        } ?: return BrowseOutcome.Failed("${peer.info.alias} is not reachable over Bluetooth")
        return when {
            res.listRes != null -> { res.listRes.pairToken?.let { trustStore.saveOutgoingToken(peer.id, it) }; BrowseOutcome.Ok(res.listRes.items, res.listRes.accessToken) }
            res.error == "pin" -> BrowseOutcome.PinRequired
            res.error == "denied" -> BrowseOutcome.Denied
            res.error == "busy" -> BrowseOutcome.Busy
            else -> BrowseOutcome.Failed(res.error ?: "Bluetooth error")
        }
    }

    suspend fun sendChat(peer: Peer, envelope: ChatEnvelope): BtChatOutcome {
        val res = withPeer(peer) { r, w -> w.message(BtMsg(BtMsg.CHAT, chat = envelope)); r.message() } ?: return BtChatOutcome.Unreachable
        return when {
            res.chatAck != null -> BtChatOutcome.Ok(res.chatAck)
            res.error == "denied" || res.error == "pin" -> BtChatOutcome.Denied
            else -> BtChatOutcome.Unreachable
        }
    }

    /** Streams one item from [offset]; [onChunk] receives the bytes. Returns bytes received in this call, or -1 when refused. */
    suspend fun fetch(peer: Peer, entry: BoxEntry, token: String, offset: Long, onChunk: (ByteArray, Int) -> Unit): Long {
        return withPeer(peer) { r, w ->
            w.message(BtMsg(BtMsg.GET, get = BtGet(entry.id, token, offset)))
            val res = r.message() ?: return@withPeer 0L
            if (res.getRes == null) return@withPeer -1L
            var got = 0L
            while (true) {
                val (kind, payload) = r.next() ?: break
                if (kind == BtFrames.END) break
                if (kind == BtFrames.DATA) { onChunk(payload, payload.size); got += payload.size }
            }
            got
        } ?: 0L
    }

    private companion object {
        const val SCAN_INTERVAL_MILLIS = 25_000L
        const val BACKOFF_MILLIS = 90_000L
        const val CONNECT_TIMEOUT_MILLIS = 12_000L
    }
}
