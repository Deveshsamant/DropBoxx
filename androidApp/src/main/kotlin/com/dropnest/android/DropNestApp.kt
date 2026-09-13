package com.dropnest.android

import android.app.Application
import com.dropnest.di.androidModule
import com.dropnest.di.engineModule
import com.dropnest.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DropNestApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DropNestApp)
            modules(androidModule(this@DropNestApp, MainActivity::class.java), engineModule, uiModule)
        }
    }
}
