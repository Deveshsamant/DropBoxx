package com.dropnest.di

import com.dropnest.ui.AppViewModel
import com.dropnest.ui.ShareInbox
import com.dropnest.ui.box.BoxViewModel
import com.dropnest.ui.chat.ChatViewModel
import com.dropnest.ui.chat.ChatsViewModel
import com.dropnest.ui.devices.DevicesViewModel
import com.dropnest.ui.devices.PeerBoxViewModel
import com.dropnest.ui.settings.SettingsViewModel
import com.dropnest.ui.transfers.TransfersViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val uiModule: Module = module {
    single { ShareInbox(get(), get(), get()) }
    viewModelOf(::AppViewModel)
    viewModelOf(::BoxViewModel)
    viewModelOf(::DevicesViewModel)
    viewModelOf(::TransfersViewModel)
    viewModelOf(::SettingsViewModel)
    viewModel { (peerId: String) -> PeerBoxViewModel(peerId, get(), get(), get()) }
    viewModelOf(::ChatsViewModel)
    viewModel { (peerId: String) -> ChatViewModel(peerId, get(), get(), get(), get()) }
}
