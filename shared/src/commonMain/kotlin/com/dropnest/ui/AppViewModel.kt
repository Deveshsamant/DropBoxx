package com.dropnest.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropnest.domain.AppSettings
import com.dropnest.domain.DeviceIdentity
import com.dropnest.domain.LocalServer
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.ServerState
import com.dropnest.domain.ThemeMode
import com.dropnest.domain.TransferEngine
import com.dropnest.model.AccessDecision
import com.dropnest.model.AccessRequest
import com.dropnest.model.DeviceInfo
import com.dropnest.model.IncomingDecision
import com.dropnest.model.IncomingRequest
import com.dropnest.model.ItemKind
import com.dropnest.model.ReceivedContent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppEvent {
    data class Received(val content: ReceivedContent) : AppEvent
    data class Toast(val message: String) : AppEvent
}

/** App-wide state: theme, identity, incoming-request dialog, received text/link events. */
class AppViewModel(
    private val engine: TransferEngine,
    private val settings: AppSettings,
    private val platform: PlatformServices,
    identity: DeviceIdentity,
    server: LocalServer,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.state.map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, settings.current.themeMode)
    val me: StateFlow<DeviceInfo> = identity.info
    val serverState: StateFlow<ServerState> = server.state
    val incomingRequest: StateFlow<IncomingRequest?> = engine.incomingRequest
    val accessRequest: StateFlow<AccessRequest?> = engine.accessRequest

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<AppEvent> get() = _events

    /** Whether the UI should copy received text to the clipboard automatically. */
    val autoCopyText: Boolean get() = settings.current.autoCopyText

    init {
        viewModelScope.launch {
            engine.receivedContent.collect { content ->
                if (content.kind == ItemKind.URL && settings.current.autoOpenLinks) platform.openUrl(content.content)
                _events.tryEmit(AppEvent.Received(content))
            }
        }
    }

    fun respond(request: IncomingRequest, accept: Boolean, trust: Boolean) {
        engine.respond(request.sessionId, IncomingDecision(accept, trust && accept))
    }

    fun respondAccess(request: AccessRequest, allow: Boolean, always: Boolean) {
        engine.respondAccess(request.id, AccessDecision(allow, always && allow))
    }

    fun openUrl(url: String) = platform.openUrl(url)

    fun toast(message: String) {
        _events.tryEmit(AppEvent.Toast(message))
    }
}
