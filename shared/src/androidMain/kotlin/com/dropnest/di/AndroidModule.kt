package com.dropnest.di

import android.content.Context
import com.dropnest.domain.PlatformServices
import com.dropnest.engine.bt.BluetoothTransport
import com.dropnest.platform.AndroidBluetoothTransport
import com.dropnest.platform.AndroidPlatformServices
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidModule(context: Context, launcherActivity: Class<*>): Module = module {
    single<Settings> { SharedPreferencesSettings(context.getSharedPreferences("dropnest", Context.MODE_PRIVATE)) }
    single<PlatformServices> { AndroidPlatformServices(context, launcherActivity) }
    single<BluetoothTransport> { AndroidBluetoothTransport(context) }
}
