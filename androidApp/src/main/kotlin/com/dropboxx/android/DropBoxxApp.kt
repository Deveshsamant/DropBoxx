package com.dropboxx.android

import android.app.Application
import com.dropboxx.di.androidModule
import com.dropboxx.di.engineModule
import com.dropboxx.di.uiModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DropBoxxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DropBoxxApp)
            modules(androidModule(this@DropBoxxApp, MainActivity::class.java), engineModule, uiModule)
        }
    }
}
