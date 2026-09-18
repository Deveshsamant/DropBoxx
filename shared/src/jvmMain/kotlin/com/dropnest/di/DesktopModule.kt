package com.dropnest.di

import com.dropnest.domain.AppSettings
import com.dropnest.domain.PlatformServices
import com.dropnest.engine.bt.BluetoothTransport
import com.dropnest.platform.DesktopPlatformServices
import com.dropnest.platform.WindowsBluetoothTransport
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.prefs.Preferences

fun desktopModule(): Module = module {
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/dropnest")) }
    single { DesktopPlatformServices { get<AppSettings>() } } bind PlatformServices::class
    single<BluetoothTransport> { WindowsBluetoothTransport() }
}
