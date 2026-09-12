package com.dropboxx.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropboxx.core.AppInfo
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.BoxAccess
import com.dropboxx.domain.DeviceIdentity
import com.dropboxx.domain.DiscoveryService
import com.dropboxx.domain.HotspotState
import com.dropboxx.domain.LocalServer
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.ServerState
import com.dropboxx.domain.SettingsState
import com.dropboxx.model.DeviceInfo
import com.dropboxx.model.Peer
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val discovery: DiscoveryService,
    private val server: LocalServer,
    private val settings: AppSettings,
    private val platform: PlatformServices,
    identity: DeviceIdentity,
) : ViewModel() {

    val peers: StateFlow<List<Peer>> = discovery.peers
    val scanning: StateFlow<Boolean> = discovery.scanning
    val serverState: StateFlow<ServerState> = server.state
    val me: StateFlow<DeviceInfo> = identity.info
    val settingsState: StateFlow<SettingsState> = settings.state
    val hotspot: StateFlow<HotspotState> = platform.hotspot.state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> get() = _messages

    init { discovery.announce() }

    fun refresh() {
        discovery.announce()
        viewModelScope.launch { discovery.scanSubnet() }
    }

    fun addByAddress(input: String) {
        val text = input.trim()
        if (text.isEmpty()) return
        val host = text.substringBefore(':')
        val port = text.substringAfter(':', "").toIntOrNull() ?: AppInfo.DEFAULT_PORT
        viewModelScope.launch {
            val peer = discovery.addManual(host, port)
            _messages.tryEmit(if (peer != null) "Found ${peer.info.alias}" else "No DropBoxx device answered at $host:$port")
        }
    }

    fun forget(peer: Peer) = discovery.forget(peer.id)
    fun toggleServer() { if (server.state.value is ServerState.Running) server.stop() else server.start() }
    fun setBoxAccess(access: BoxAccess) = settings.update { copy(boxAccess = access) }
    fun toggleHotspot() {
        if (hotspot.value.active || hotspot.value.starting) platform.hotspot.stop()
        else viewModelScope.launch { platform.hotspot.start() }
    }
}
