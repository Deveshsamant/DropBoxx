package com.dropnest.ui

import com.dropnest.model.OutgoingItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Items handed to the app from outside (Android share sheet, Windows "open with" / command line).
 * The home screen drains it into the staging area.
 */
class ShareInbox {
    private val _pending = MutableStateFlow<List<OutgoingItem>>(emptyList())
    val pending: StateFlow<List<OutgoingItem>> get() = _pending

    fun offer(items: List<OutgoingItem>) {
        if (items.isEmpty()) return
        _pending.update { it + items }
    }

    fun drain(): List<OutgoingItem> {
        val items = _pending.value
        _pending.value = emptyList()
        return items
    }
}
