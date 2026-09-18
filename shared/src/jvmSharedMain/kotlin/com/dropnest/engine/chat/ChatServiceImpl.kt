package com.dropnest.engine.chat

import co.touchlab.kermit.Logger
import com.dropnest.core.nowMillis
import com.dropnest.core.randomId
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.ChatService
import com.dropnest.domain.DeviceIdentity
import com.dropnest.domain.DiscoveryService
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.TrustStore
import com.dropnest.engine.bt.BluetoothPeerService
import com.dropnest.engine.bt.BtChatOutcome
import com.dropnest.engine.net.PeerClients
import com.dropnest.engine.store.JsonFileStore
import com.dropnest.model.Api
import com.dropnest.model.ChatAck
import com.dropnest.model.ChatAttachment
import com.dropnest.model.ChatEnvelope
import com.dropnest.model.ChatMessage
import com.dropnest.model.ChatWire
import com.dropnest.model.Conversation
import com.dropnest.model.DeviceInfo
import com.dropnest.model.MessageStatus
import com.dropnest.model.OutgoingItem
import com.dropnest.model.Peer
import com.dropnest.model.PlatformFile
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class ChatFile(
    /** Last known identity of every device we have a thread with (so threads survive the peer going offline). */
    val peers: Map<String, DeviceInfo> = emptyMap(),
    val messages: List<ChatMessage> = emptyList(),
)

sealed interface ChatReceiveResult {
    data class Ok(val ack: ChatAck) : ChatReceiveResult
    data object PinRequired : ChatReceiveResult
    data object Denied : ChatReceiveResult
    data object Busy : ChatReceiveResult
}

/**
 * Store-and-forward messaging between two DropNest devices that trust each other. Outgoing
 * messages are queued locally and pushed the moment discovery sees the peer; the receiver only
 * accepts them from a device it chose "Always allow" for (pair token check, never a prompt).
 */
class ChatServiceImpl(
    dataDirectory: String,
    private val identity: DeviceIdentity,
    private val trustStore: TrustStore,
    private val discovery: DiscoveryService,
    private val clients: PeerClients,
    private val platform: PlatformServices,
    private val box: BoxRepository,
    private val scope: CoroutineScope,
) : ChatService {

    private val log = Logger.withTag("Chat")
    private val store = JsonFileStore(File(dataDirectory, "chat.json"), ChatFile.serializer()) { ChatFile() }
    private var data = store.load()
    private val lock = Any()

    private val _messages = MutableStateFlow(data.messages)
    override val messages: StateFlow<List<ChatMessage>> get() = _messages
    private val _incoming = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 32)
    override val incoming: SharedFlow<ChatMessage> get() = _incoming
    override val activeThread = MutableStateFlow<String?>(null)

    private val peersFlow = MutableStateFlow(data.peers)

    override val conversations: StateFlow<List<Conversation>> =
        combine(_messages, peersFlow, discovery.peers) { msgs, known, online ->
            val onlineIds = online.map { it.id }.toSet()
            val byPeer = msgs.groupBy { it.peerId }
            val ids = (byPeer.keys + known.keys)
            ids.mapNotNull { id ->
                val info = online.firstOrNull { it.id == id }?.info ?: known[id] ?: return@mapNotNull null
                val list = byPeer[id].orEmpty()
                Conversation(
                    peer = info,
                    lastMessage = list.maxByOrNull { it.sentAt },
                    unread = list.count { !it.fromMe && !it.read },
                    online = id in onlineIds,
                    pending = list.count { it.fromMe && it.status == MessageStatus.PENDING },
                )
            }.sortedByDescending { it.lastMessage?.sentAt ?: 0L }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val flushing = ConcurrentHashMap<String, Mutex>()
    /** Set by the engine module; null when the platform has no Bluetooth. */
    var bluetooth: BluetoothPeerService? = null

    init {
        // Push whatever is queued as soon as a peer shows up, and retry periodically while it stays around.
        scope.launch { discovery.peers.collect { peers -> peers.forEach { p -> if (hasPending(p.id)) flush(p) } } }
        scope.launch {
            while (true) {
                delay(RETRY_MILLIS)
                discovery.peers.value.forEach { p -> if (hasPending(p.id)) flush(p) }
            }
        }
    }

    // ---- outgoing ----

    override fun canMessage(peerId: String): Boolean = trustStore.outgoingToken(peerId) != null

    override fun send(peer: DeviceInfo, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        val msg = ChatMessage(randomId(12), peer.id, fromMe = true, text = t, sentAt = nowMillis(), status = MessageStatus.PENDING)
        mutate { copy(peers = peers + (peer.id to peer), messages = messages + msg) }
        discovery.peers.value.firstOrNull { it.id == peer.id }?.let { flush(it) }
    }

    override suspend fun sendFiles(peer: DeviceInfo, files: List<PlatformFile>): List<String> {
        if (files.isEmpty()) return emptyList()
        val before = box.items.value.map { it.id }.toSet()
        val skipped = box.add(files.map { OutgoingItem.File(randomId(8), it) }, forPeer = peer)
        val added = box.items.value.filter { it.id !in before && it.forPeerId == peer.id }
        val msgs = added.map {
            ChatMessage(randomId(12), peer.id, fromMe = true, text = it.name, sentAt = nowMillis(), status = MessageStatus.PENDING,
                attachment = ChatAttachment(it.id, it.name, it.size, it.mimeType, it.kind))
        }
        if (msgs.isNotEmpty()) {
            mutate { copy(peers = peers + (peer.id to peer), messages = messages + msgs) }
            discovery.peers.value.firstOrNull { it.id == peer.id }?.let { flush(it) }
        }
        return skipped
    }

    override fun attachmentSaved(messageId: String, localPath: String) =
        mutate { copy(messages = messages.map { if (it.id == messageId) it.copy(localPath = localPath) else it }) }

    override fun retry(messageId: String) {
        val msg = _messages.value.firstOrNull { it.id == messageId } ?: return
        mutate { copy(messages = messages.map { if (it.id == messageId) it.copy(status = MessageStatus.PENDING, error = null) else it }) }
        discovery.peers.value.firstOrNull { it.id == msg.peerId }?.let { flush(it) }
    }

    private fun hasPending(peerId: String) = _messages.value.any { it.peerId == peerId && it.fromMe && it.status == MessageStatus.PENDING }

    private fun flush(peer: Peer) {
        val mutex = flushing.getOrPut(peer.id) { Mutex() }
        if (mutex.isLocked) return
        scope.launch(Dispatchers.IO) {
            mutex.withLock {
                val batch = _messages.value.filter { it.peerId == peer.id && it.fromMe && it.status == MessageStatus.PENDING }.sortedBy { it.sentAt }
                if (batch.isEmpty()) return@withLock
                val envelope = ChatEnvelope(identity.info.value, trustStore.outgoingToken(peer.id), null, batch.map { ChatWire(it.id, it.text, it.sentAt, it.attachment) })
                if (peer.viaBluetooth) {
                    when (val r = bluetooth?.sendChat(peer, envelope) ?: BtChatOutcome.Unreachable) {
                        is BtChatOutcome.Ok -> { val done = r.ack.accepted.toSet(); mutate { copy(peers = peers + (peer.id to peer.info), messages = messages.map { if (it.id in done) it.copy(status = MessageStatus.SENT, error = null) else it }) } }
                        BtChatOutcome.Denied -> fail(batch, "${peer.info.alias} hasn't trusted this device - ask them to choose \"Always allow\"")
                        BtChatOutcome.Unreachable -> log.d { "${peer.info.alias} not reachable over Bluetooth yet" }
                    }
                    return@withLock
                }
                val response = try {
                    clients.forFingerprint(peer.info.fingerprint).post("${peer.baseUrl}${Api.CHAT}") {
                        contentType(ContentType.Application.Json); setBody(envelope)
                    }
                } catch (e: CancellationException) { throw e } catch (e: Exception) {
                    log.d { "${peer.info.alias} not reachable yet: ${e.message}" }; return@withLock
                }
                when (response.status) {
                    HttpStatusCode.OK -> {
                        val ack: ChatAck = response.body()
                        ack.pairToken?.let { trustStore.saveOutgoingToken(peer.id, it) }
                        val done = ack.accepted.toSet()
                        mutate { copy(peers = peers + (peer.id to peer.info), messages = messages.map { if (it.id in done) it.copy(status = MessageStatus.SENT, error = null) else it }) }
                    }
                    HttpStatusCode.Forbidden -> fail(batch, "${peer.info.alias} hasn't trusted this device - ask them to choose \"Always allow\"")
                    HttpStatusCode.Unauthorized -> fail(batch, "${peer.info.alias} requires a PIN")
                    HttpStatusCode.Conflict -> Unit // busy answering another prompt; the retry loop will try again
                    else -> fail(batch, "${peer.info.alias} answered ${response.status.value}")
                }
            }
        }
    }

    private fun fail(batch: List<ChatMessage>, reason: String) {
        val ids = batch.map { it.id }.toSet()
        mutate { copy(messages = messages.map { if (it.id in ids) it.copy(status = MessageStatus.FAILED, error = reason) else it }) }
    }

    // ---- incoming (called by the server) ----

    /** Messages are accepted only from devices this user chose "Always allow" for - no prompt, ever. */
    suspend fun receive(envelope: ChatEnvelope, remoteAddress: String): ChatReceiveResult {
        val from = envelope.from
        val trusted = trustStore.find(from.id)?.takeIf { it.fingerprint == from.fingerprint && it.pairToken == envelope.pairToken }
        if (trusted == null) { log.i { "Message from untrusted ${from.alias} refused" }; return ChatReceiveResult.Denied }
        val known = _messages.value.map { it.id }.toSet()
        val reading = activeThread.value == from.id
        val fresh = envelope.messages.filter { it.id !in known }
            .map { ChatMessage(it.id, from.id, fromMe = false, text = it.text, sentAt = it.sentAt, status = MessageStatus.SENT, read = reading, attachment = it.attachment) }
        if (fresh.isNotEmpty()) {
            mutate { copy(peers = peers + (from.id to from), messages = (messages + fresh).takeLast(MAX_MESSAGES)) }
            fresh.forEach { _incoming.tryEmit(it) }
            if (!reading) platform.notify(from.alias, fresh.joinToString("\n") { if (it.attachment != null) "File: ${it.text}" else it.text }.take(300))
            log.i { "${fresh.size} message(s) from ${from.alias}" }
        }
        return ChatReceiveResult.Ok(ChatAck(envelope.messages.map { it.id }))
    }

    // ---- housekeeping ----

    override fun markRead(peerId: String) {
        if (_messages.value.none { it.peerId == peerId && !it.fromMe && !it.read }) return
        mutate { copy(messages = messages.map { if (it.peerId == peerId && !it.fromMe) it.copy(read = true) else it }) }
    }

    override fun clearThread(peerId: String) = mutate { copy(peers = peers - peerId, messages = messages.filterNot { it.peerId == peerId }) }

    private fun mutate(block: ChatFile.() -> ChatFile) {
        synchronized(lock) {
            data = data.block()
            _messages.value = data.messages
            peersFlow.value = data.peers
            store.save(data)
        }
    }

    private companion object {
        const val RETRY_MILLIS = 15_000L
        const val MAX_MESSAGES = 5_000
    }
}
