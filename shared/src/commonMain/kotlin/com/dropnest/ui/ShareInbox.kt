package com.dropnest.ui

import co.touchlab.kermit.Logger
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.PlatformServices
import com.dropnest.model.OutgoingItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Items handed to the app from outside - Android share sheet (WhatsApp, Instagram, Gallery...),
 * Windows "Open with" / command line. They are imported into the box immediately by the engine,
 * regardless of which screen is open, and kept until the user deletes them.
 */
class ShareInbox(private val box: BoxRepository, private val platform: PlatformServices, private val scope: CoroutineScope) {

    private val log = Logger.withTag("ShareInbox")

    private val _importing = MutableStateFlow(0)
    /** Number of imports in flight (for a progress hint in the UI). */
    val importing: StateFlow<Int> get() = _importing

    private val _results = MutableSharedFlow<String>(extraBufferCapacity = 16)
    /** Human-readable outcome of each import, for a snackbar. */
    val results: SharedFlow<String> get() = _results

    fun offer(items: List<OutgoingItem>) {
        if (items.isEmpty()) return
        _importing.value++
        scope.launch {
            try {
                val skipped = box.add(items)
                val added = items.size - skipped.size
                val message = when {
                    added == 0 -> "Could not read ${skipped.joinToString().take(60)}"
                    skipped.isEmpty() -> "Saved to your box: ${describe(items)}"
                    else -> "Saved $added, could not read ${skipped.size}"
                }
                _results.tryEmit(message)
                if (added > 0) platform.notify("Saved to your box", describe(items))
            } catch (e: Exception) {
                log.w(e) { "import failed" }
                _results.tryEmit("Could not save: ${e.message}")
            } finally {
                _importing.value--
            }
        }
    }

    private fun describe(items: List<OutgoingItem>): String = when {
        items.size == 1 -> when (val i = items.first()) {
            is OutgoingItem.File -> i.file.name
            is OutgoingItem.Url -> "link"
            is OutgoingItem.Text -> "text"
        }
        else -> "${items.size} items"
    }
}
