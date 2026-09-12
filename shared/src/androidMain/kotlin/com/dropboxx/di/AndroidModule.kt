package com.dropboxx.di

import android.content.Context
import com.dropboxx.domain.PlatformServices
import com.dropboxx.platform.AndroidPlatformServices
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidModule(context: Context, launcherActivity: Class<*>): Module = module {
    single<Settings> { SharedPreferencesSettings(context.getSharedPreferences("dropboxx", Context.MODE_PRIVATE)) }
    single<PlatformServices> { AndroidPlatformServices(context, launcherActivity) }
}
