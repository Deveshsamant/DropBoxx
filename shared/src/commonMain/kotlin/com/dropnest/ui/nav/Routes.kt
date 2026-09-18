package com.dropnest.ui.nav

import kotlinx.serialization.Serializable

sealed interface Route {
    @Serializable data object Box : Route
    @Serializable data object Devices : Route
    @Serializable data class PeerBox(val peerId: String) : Route
    @Serializable data object Chats : Route
    @Serializable data class Chat(val peerId: String) : Route
    @Serializable data object Transfers : Route
    @Serializable data object Settings : Route
}
