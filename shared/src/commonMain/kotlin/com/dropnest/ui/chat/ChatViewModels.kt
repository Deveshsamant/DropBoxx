package com.dropnest.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropnest.domain.ChatService
import com.dropnest.domain.DiscoveryService
import com.dropnest.domain.BrowseOutcome
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.TransferEngine
import com.dropnest.model.ChatMessage
import com.dropnest.model.Conversation
import com.dropnest.model.DeviceInfo
import com.dropnest.model.Peer
import com.dropnest.model.PlatformFile
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ChatsUiState(
    val conversations: List<Conversation> = emptyList(),
    /** Nearby trusted devices you have not chatted with yet. */
    val newPeers: List<Peer> = emptyList(),
    /** Nearby devices that have not trusted this one yet (open their box and ask for "Always allow"). */
    val untrusted: List<Peer> = emptyList(),
)

/** The list of threads plus nearby devices to start one with. */
class ChatsViewModel(chat: ChatService, discovery: DiscoveryService) : ViewModel() {
    val state: StateFlow<ChatsUiState> = combine(chat.conversations, discovery.peers) { convs, peers ->
        val known = convs.map { it.peer.id }.toSet()
        val fresh = peers.filter { it.id !in known }
        ChatsUiState(convs, fresh.filter { chat.canMessage(it.id) }, fresh.filter { !chat.canMessage(it.id) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatsUiState())
}

data class ChatUiState(
    val peer: DeviceInfo? = null,
    val online: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    /** False until the peer chose "Always allow" for this device. */
    val canMessage: Boolean = true,
)

/** One thread. Marks messages read while open and tells the service which thread is on screen. */
class ChatViewModel(
    private val peerId: String,
    private val chat: ChatService,
    private val discovery: DiscoveryService,
    private val platform: PlatformServices,
    private val engine: TransferEngine,
) : ViewModel() {

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> get() = _messages
    private val _fetching = MutableStateFlow<Set<String>>(emptySet())
    val fetching: StateFlow<Set<String>> get() = _fetching

    val state: StateFlow<ChatUiState> = combine(chat.conversations, discovery.peers, chat.messages) { convs, peers, msgs ->
        val online = peers.firstOrNull { it.id == peerId }
        val info = online?.info ?: convs.firstOrNull { it.peer.id == peerId }?.peer
        ChatUiState(info, online != null, msgs.filter { it.peerId == peerId }.sortedBy { it.sentAt }, chat.canMessage(peerId))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    fun opened() { chat.activeThread.value = peerId; chat.markRead(peerId) }
    fun closed() { if (chat.activeThread.value == peerId) chat.activeThread.value = null }
    fun seen() = chat.markRead(peerId)

    fun send(text: String) { state.value.peer?.let { chat.send(it, text) } }

    /** Paperclip: pick files and drop them privately for this device. */
    fun pickFiles() { viewModelScope.launch { sendFiles(runCatching { platform.filePicker.pickFiles() }.getOrDefault(emptyList())) } }

    fun sendFiles(files: List<PlatformFile>) {
        val peer = state.value.peer ?: return
        if (files.isEmpty()) return
        viewModelScope.launch {
            val skipped = chat.sendFiles(peer, files)
            if (skipped.isNotEmpty()) _messages.tryEmit("Could not read ${skipped.joinToString()}")
        }
    }

    /** Receiver: pull the private drop from the peer's box (trusted, so no prompt) and remember where it landed. */
    fun fetch(message: ChatMessage) {
        val att = message.attachment ?: return
        val peer = discovery.peers.value.firstOrNull { it.id == peerId }
        if (peer == null) { _messages.tryEmit("${state.value.peer?.alias ?: "Device"} is not nearby right now"); return }
        if (message.id in _fetching.value) return
        _fetching.update { it + message.id }
        viewModelScope.launch {
            try {
                when (val r = engine.browse(peer)) {
                    is BrowseOutcome.Ok -> {
                        val entry = r.items.firstOrNull { it.id == att.itemId }
                        if (entry == null) { _messages.tryEmit("${att.name} is no longer in ${peer.info.alias}'s box"); return@launch }
                        val sessionId = engine.download(peer, listOf(entry), r.accessToken)
                        val session = withTimeoutOrNull(10 * 60_000L) {
                            engine.sessions.first { l -> l.any { it.id == sessionId && it.status.isTerminal } }.first { it.id == sessionId }
                        }
                        val path = session?.items?.firstOrNull { it.id == entry.id }?.resultPath
                        if (path != null) chat.attachmentSaved(message.id, path) else _messages.tryEmit("Could not fetch ${att.name}")
                    }
                    else -> _messages.tryEmit("Could not open ${peer.info.alias}'s box")
                }
            } finally { _fetching.update { it - message.id } }
        }
    }
    fun retry(id: String) = chat.retry(id)
    fun clear() = chat.clearThread(peerId)
    fun openUrl(url: String) = platform.openUrl(url)
}
