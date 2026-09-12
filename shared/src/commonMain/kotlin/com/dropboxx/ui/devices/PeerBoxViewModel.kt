package com.dropboxx.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropboxx.domain.BrowseOutcome
import com.dropboxx.domain.DiscoveryService
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.TransferEngine
import com.dropboxx.model.BoxEntry
import com.dropboxx.model.ItemKind
import com.dropboxx.model.Peer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PeerBoxStatus {
    data object Loading : PeerBoxStatus
    data object WaitingForApproval : PeerBoxStatus
    data class Ready(val items: List<BoxEntry>, val accessToken: String) : PeerBoxStatus
    data object PinRequired : PeerBoxStatus
    data object Denied : PeerBoxStatus
    data object Busy : PeerBoxStatus
    data class Failed(val message: String) : PeerBoxStatus
}

data class PeerBoxUiState(
    val peer: Peer? = null,
    val status: PeerBoxStatus = PeerBoxStatus.Loading,
    val selected: Set<String> = emptySet(),
)

/** One peer's box: list what they dropped, pick items, fetch them. */
class PeerBoxViewModel(
    private val peerId: String,
    private val engine: TransferEngine,
    discovery: DiscoveryService,
    private val platform: PlatformServices,
) : ViewModel() {

    private val status = MutableStateFlow<PeerBoxStatus>(PeerBoxStatus.Loading)
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val peer: StateFlow<Peer?> = discovery.peers.map { list -> list.firstOrNull { it.id == peerId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, discovery.peers.value.firstOrNull { it.id == peerId })

    val state: StateFlow<PeerBoxUiState> = combine(peer, status, selected) { p, s, sel -> PeerBoxUiState(p, s, sel) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeerBoxUiState(peer.value))

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> get() = _messages

    init { load() }

    fun load(pin: String? = null) {
        val p = peer.value ?: run { status.value = PeerBoxStatus.Failed("Device is no longer nearby"); return }
        status.value = if (p.trusted) PeerBoxStatus.Loading else PeerBoxStatus.WaitingForApproval
        viewModelScope.launch {
            status.value = when (val r = engine.browse(p, pin)) {
                is BrowseOutcome.Ok -> PeerBoxStatus.Ready(r.items.sortedByDescending { it.addedAt }, r.accessToken)
                BrowseOutcome.PinRequired -> PeerBoxStatus.PinRequired
                BrowseOutcome.Denied -> PeerBoxStatus.Denied
                BrowseOutcome.Busy -> PeerBoxStatus.Busy
                is BrowseOutcome.Failed -> PeerBoxStatus.Failed(r.message)
            }
        }
    }

    fun toggle(id: String) = selected.update { if (id in it) it - id else it + id }
    fun selectAll() { (status.value as? PeerBoxStatus.Ready)?.let { r -> selected.value = r.items.map { it.id }.toSet() } }
    fun clearSelection() { selected.value = emptySet() }

    /** Fetches the selection (or everything when nothing is selected). */
    fun fetch() {
        val p = peer.value ?: return
        val ready = status.value as? PeerBoxStatus.Ready ?: return
        val ids = selected.value
        val entries = if (ids.isEmpty()) ready.items else ready.items.filter { it.id in ids }
        if (entries.isEmpty()) return
        engine.download(p, entries, ready.accessToken)
        selected.value = emptySet()
        val files = entries.count { it.kind == ItemKind.FILE }
        _messages.tryEmit(if (files > 0) "Fetching $files file${if (files == 1) "" else "s"} from ${p.info.alias}" else "Received from ${p.info.alias}")
    }

    fun fetchOne(entry: BoxEntry) {
        val p = peer.value ?: return
        val ready = status.value as? PeerBoxStatus.Ready ?: return
        engine.download(p, listOf(entry), ready.accessToken)
        if (entry.kind == ItemKind.FILE) _messages.tryEmit("Fetching ${entry.name}")
    }

    fun openUrl(url: String) = platform.openUrl(url)
}
