package com.dropboxx.di

import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.PlatformServices
import com.dropboxx.platform.DesktopPlatformServices
import com.russhwolf.settings.PreferencesSettings
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.prefs.Preferences

fun desktopModule(): Module = module {
    single<Settings> { PreferencesSettings(Preferences.userRoot().node("com/dropboxx")) }
    single { DesktopPlatformServices { get<AppSettings>() } } bind PlatformServices::class
}
