package com.dropboxx.domain

import com.dropboxx.core.AppInfo
import com.dropboxx.core.randomId
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Who may open this device's box. */
enum class BoxAccess { ASK, TRUSTED_ONLY, EVERYONE }

data class SettingsState(
    val alias: String,
    val port: Int,
    /** Auto-accept from trusted devices without asking. */
    val quickSave: Boolean,
    /** Senders must supply this PIN, empty = disabled. */
    val pin: String,
    val autoCopyText: Boolean,
    val autoOpenLinks: Boolean,
    val saveDirectory: String,
    val themeMode: ThemeMode,
    val minimizeToTray: Boolean,
    val launchAtStartup: Boolean,
    val onboardingDone: Boolean,
    val boxAccess: BoxAccess,
)

/** Persisted user preferences. Cheap to read, observable as a StateFlow. */
class AppSettings(private val settings: Settings, defaultAlias: String, defaultSaveDirectory: String) {

    private val _state = MutableStateFlow(load(defaultAlias, defaultSaveDirectory))
    val state: StateFlow<SettingsState> = _state
    val current: SettingsState get() = _state.value

    private fun load(defaultAlias: String, defaultSaveDirectory: String) = SettingsState(
        alias = settings.getString(K_ALIAS, defaultAlias),
        port = settings.getInt(K_PORT, AppInfo.DEFAULT_PORT),
        quickSave = settings.getBoolean(K_QUICK_SAVE, true),
        pin = settings.getString(K_PIN, ""),
        autoCopyText = settings.getBoolean(K_AUTO_COPY, true),
        autoOpenLinks = settings.getBoolean(K_AUTO_OPEN_LINKS, false),
        saveDirectory = settings.getString(K_SAVE_DIR, defaultSaveDirectory),
        themeMode = runCatching { ThemeMode.valueOf(settings.getString(K_THEME, ThemeMode.SYSTEM.name)) }.getOrDefault(ThemeMode.SYSTEM),
        minimizeToTray = settings.getBoolean(K_MIN_TO_TRAY, true),
        launchAtStartup = settings.getBoolean(K_LAUNCH_AT_STARTUP, false),
        onboardingDone = settings.getBoolean(K_ONBOARDING, false),
        boxAccess = runCatching { BoxAccess.valueOf(settings.getString(K_BOX_ACCESS, BoxAccess.ASK.name)) }.getOrDefault(BoxAccess.ASK),
    )

    fun update(block: SettingsState.() -> SettingsState) {
        _state.update { it.block() }
        persist(_state.value)
    }

    private fun persist(s: SettingsState) {
        settings.putString(K_ALIAS, s.alias)
        settings.putInt(K_PORT, s.port)
        settings.putBoolean(K_QUICK_SAVE, s.quickSave)
        settings.putString(K_PIN, s.pin)
        settings.putBoolean(K_AUTO_COPY, s.autoCopyText)
        settings.putBoolean(K_AUTO_OPEN_LINKS, s.autoOpenLinks)
        settings.putString(K_SAVE_DIR, s.saveDirectory)
        settings.putString(K_THEME, s.themeMode.name)
        settings.putBoolean(K_MIN_TO_TRAY, s.minimizeToTray)
        settings.putBoolean(K_LAUNCH_AT_STARTUP, s.launchAtStartup)
        settings.putBoolean(K_ONBOARDING, s.onboardingDone)
        settings.putString(K_BOX_ACCESS, s.boxAccess.name)
    }

    /** Stable per-install id; created once. */
    val installId: String
        get() = settings.getStringOrNull(K_INSTALL_ID) ?: randomId().also { settings.putString(K_INSTALL_ID, it) }

    private companion object {
        const val K_ALIAS = "alias"
        const val K_PORT = "port"
        const val K_QUICK_SAVE = "quick_save"
        const val K_PIN = "pin"
        const val K_AUTO_COPY = "auto_copy_text"
        const val K_AUTO_OPEN_LINKS = "auto_open_links"
        const val K_SAVE_DIR = "save_dir"
        const val K_THEME = "theme"
        const val K_MIN_TO_TRAY = "minimize_to_tray"
        const val K_LAUNCH_AT_STARTUP = "launch_at_startup"
        const val K_ONBOARDING = "onboarding_done"
        const val K_INSTALL_ID = "install_id"
        const val K_BOX_ACCESS = "box_access"
    }
}
