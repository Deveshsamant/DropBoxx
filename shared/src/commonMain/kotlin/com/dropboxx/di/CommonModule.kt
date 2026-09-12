package com.dropboxx.di

import com.dropboxx.ui.AppViewModel
import com.dropboxx.ui.ShareInbox
import com.dropboxx.ui.box.BoxViewModel
import com.dropboxx.ui.devices.DevicesViewModel
import com.dropboxx.ui.devices.PeerBoxViewModel
import com.dropboxx.ui.settings.SettingsViewModel
import com.dropboxx.ui.transfers.TransfersViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule: Module = module {
    single { ShareInbox() }
    viewModelOf(::AppViewModel)
    viewModelOf(::BoxViewModel)
    viewModelOf(::DevicesViewModel)
    viewModelOf(::TransfersViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { (peerId: String) -> PeerBoxViewModel(peerId, get(), get(), get()) }
}
