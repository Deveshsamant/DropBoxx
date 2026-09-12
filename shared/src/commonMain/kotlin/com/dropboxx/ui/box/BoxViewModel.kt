package com.dropboxx.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropboxx.core.randomId
import com.dropboxx.domain.BoxRepository
import com.dropboxx.domain.DiscoveryService
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.SendOutcome
import com.dropboxx.domain.TransferEngine
import com.dropboxx.model.BoxItem
import com.dropboxx.model.OutgoingItem
import com.dropboxx.model.Peer
import com.dropboxx.model.PlatformFile
import com.dropboxx.model.looksLikeUrl
import com.dropboxx.ui.ShareInbox
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BoxUiState(
    val items: List<BoxItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val peers: List<Peer> = emptyList(),
    val importing: Boolean = false,
    val sendingTo: String? = null,
    val pinPrompt: Peer? = null,
    val dropHover: Boolean = false,
) {
    val totalBytes: Long get() = items.sumOf { it.size }
    /** What a send will carry: the selection, or everything when nothing is selected. */
    val sendIds: Set<String> get() = if (selected.isEmpty()) items.map { it.id }.toSet() else selected
}

class BoxViewModel(
    private val box: BoxRepository,
    private val engine: TransferEngine,
    private val discovery: DiscoveryService,
    private val platform: PlatformServices,
    private val inbox: ShareInbox,
) : ViewModel() {

    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val importing = MutableStateFlow(false)
    private val sendingTo = MutableStateFlow<String?>(null)
    private val pinPrompt = MutableStateFlow<Peer?>(null)
    private val dropHover = MutableStateFlow(false)

    val state: StateFlow<BoxUiState> = combine(box.items, selected, discovery.peers, importing, sendingTo, pinPrompt, dropHover) { v ->
        @Suppress("UNCHECKED_CAST")
        BoxUiState(
            items = (v[0] as List<BoxItem>).sortedByDescending { it.addedAt },
            selected = v[1] as Set<String>,
            peers = v[2] as List<Peer>,
            importing = v[3] as Boolean,
            sendingTo = v[4] as String?,
            pinPrompt = v[5] as Peer?,
            dropHover = v[6] as Boolean,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoxUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> get() = _messages

    init {
        viewModelScope.launch { inbox.pending.collect { if (it.isNotEmpty()) add(inbox.drain()) } }
    }

    fun addFiles(files: List<PlatformFile>) = add(files.map { OutgoingItem.File(randomId(8), it) })

    fun addText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        add(listOf(if (t.looksLikeUrl()) OutgoingItem.Url(randomId(8), t) else OutgoingItem.Text(randomId(8), t)))
    }

    private fun add(items: List<OutgoingItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            try {
                val skipped = box.add(items)
                if (skipped.isNotEmpty()) _messages.tryEmit("Could not read ${skipped.size} item${if (skipped.size == 1) "" else "s"}: ${skipped.joinToString().take(80)}")
            } finally {
                importing.value = false
            }
        }
    }

    fun pickFiles() {
        viewModelScope.launch { addFiles(runCatching { platform.filePicker.pickFiles() }.getOrDefault(emptyList())) }
    }

    fun remove(id: String) { box.remove(id); selected.update { it - id } }
    fun clear() { box.clear(); selected.value = emptySet() }
    fun toggle(id: String) = selected.update { if (id in it) it - id else it + id }
    fun clearSelection() { selected.value = emptySet() }
    fun setDropHover(hover: Boolean) { dropHover.value = hover }
    fun open(item: BoxItem) { if (item.kind == com.dropboxx.model.ItemKind.URL) platform.openUrl(item.content.orEmpty()) else item.source?.let(platform::openFile) }

    fun sendTo(peer: Peer, pin: String? = null) {
        val ids = state.value.sendIds
        if (ids.isEmpty()) { _messages.tryEmit("Your box is empty - drop something first"); return }
        if (sendingTo.value != null) return
        pinPrompt.value = null
        sendingTo.value = peer.id
        viewModelScope.launch {
            try {
                val outgoing = box.toOutgoing(ids)
                if (outgoing.size < ids.size) _messages.tryEmit("${ids.size - outgoing.size} item(s) are no longer available and were skipped")
                when (val outcome = engine.send(peer, outgoing, pin)) {
                    SendOutcome.Started -> { selected.value = emptySet(); _messages.tryEmit("Sending ${outgoing.size} item${if (outgoing.size == 1) "" else "s"} to ${peer.info.alias}") }
                    SendOutcome.PinRequired -> pinPrompt.value = peer
                    SendOutcome.Declined -> _messages.tryEmit("${peer.info.alias} declined")
                    SendOutcome.Busy -> _messages.tryEmit("${peer.info.alias} is busy with another request")
                    is SendOutcome.Failed -> _messages.tryEmit(outcome.message)
                }
            } finally {
                sendingTo.value = null
            }
        }
    }

    fun dismissPin() { pinPrompt.value = null }
}
