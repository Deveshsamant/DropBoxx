package com.dropnest.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropnest.domain.AppSettings
import com.dropnest.domain.BoxAccess
import com.dropnest.domain.DeviceIdentity
import com.dropnest.domain.LocalServer
import com.dropnest.domain.PlatformServices
import com.dropnest.domain.SettingsState
import com.dropnest.domain.ThemeMode
import com.dropnest.domain.TrustStore
import com.dropnest.domain.TrustedDevice
import com.dropnest.model.DeviceInfo
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: AppSettings,
    private val trustStore: TrustStore,
    private val server: LocalServer,
    private val platform: PlatformServices,
    identity: DeviceIdentity,
) : ViewModel() {

    val state: StateFlow<SettingsState> = settings.state
    val trusted: StateFlow<List<TrustedDevice>> = trustStore.devices
    val me: StateFlow<DeviceInfo> = identity.info
    val canPickDirectory: Boolean get() = platform.filePicker.canPickDirectory
    val supportsTray: Boolean get() = platform.supportsTray

    fun setAlias(alias: String) = settings.update { copy(alias = alias.trim().ifEmpty { platform.defaultAlias }) }
    fun setQuickSave(enabled: Boolean) = settings.update { copy(quickSave = enabled) }
    fun setBoxAccess(access: BoxAccess) = settings.update { copy(boxAccess = access) }
    fun setPin(pin: String) = settings.update { copy(pin = pin.filter { it.isDigit() }.take(8)) }
    fun setAutoCopyText(enabled: Boolean) = settings.update { copy(autoCopyText = enabled) }
    fun setAutoOpenLinks(enabled: Boolean) = settings.update { copy(autoOpenLinks = enabled) }
    fun setTheme(mode: ThemeMode) = settings.update { copy(themeMode = mode) }
    fun setMinimizeToTray(enabled: Boolean) = settings.update { copy(minimizeToTray = enabled) }

    fun setLaunchAtStartup(enabled: Boolean) {
        if (platform.setLaunchAtStartup(enabled)) settings.update { copy(launchAtStartup = enabled) }
    }

    fun setPort(port: Int) {
        if (port !in 1024..65535 || port == settings.current.port) return
        settings.update { copy(port = port) }
        server.restart()
    }

    fun pickSaveDirectory() {
        viewModelScope.launch {
            platform.filePicker.pickDirectory()?.let { dir -> settings.update { copy(saveDirectory = dir) } }
        }
    }

    fun revoke(id: String) = trustStore.revoke(id)
}
