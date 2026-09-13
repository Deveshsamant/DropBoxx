package com.dropnest.ui.box

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropnest.core.randomId
import com.dropnest.domain.BoxRepository
import com.dropnest.domain.PlatformServices
import com.dropnest.model.BoxItem
import com.dropnest.model.ItemKind
import com.dropnest.model.OutgoingItem
import com.dropnest.model.PlatformFile
import com.dropnest.model.looksLikeUrl
import com.dropnest.ui.ShareInbox
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BoxUiState(
    val items: List<BoxItem> = emptyList(),
    val importing: Boolean = false,
    val dropHover: Boolean = false,
) {
    val totalBytes: Long get() = items.sumOf { it.size }
}

/** My box: whatever is dropped here is offered to allowed devices; nothing is pushed. */
class BoxViewModel(
    private val box: BoxRepository,
    private val platform: PlatformServices,
    private val inbox: ShareInbox,
) : ViewModel() {

    private val importing = MutableStateFlow(false)
    private val dropHover = MutableStateFlow(false)

    val state: StateFlow<BoxUiState> = combine(box.items, importing, inbox.importing, dropHover) { items, imp, shared, hover ->
        BoxUiState(items.sortedByDescending { it.addedAt }, imp || shared > 0, hover)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BoxUiState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> get() = _messages



    fun addFiles(files: List<PlatformFile>) = add(files.map { OutgoingItem.File(randomId(8), it) })

    fun addText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        add(listOf(if (t.looksLikeUrl()) OutgoingItem.Url(randomId(8), t) else OutgoingItem.Text(randomId(8), t)))
    }

    private fun add(items: List<OutgoingItem>) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            importing.value = true
            try {
                val skipped = box.add(items)
                if (skipped.isNotEmpty()) _messages.tryEmit("Could not read ${skipped.size} item${if (skipped.size == 1) "" else "s"}: ${skipped.joinToString().take(80)}")
                else _messages.tryEmit("Added to your box - nearby devices can pick it up")
            } finally {
                importing.value = false
            }
        }
    }

    fun pickFiles() {
        viewModelScope.launch { addFiles(runCatching { platform.filePicker.pickFiles() }.getOrDefault(emptyList())) }
    }

    fun remove(id: String) = box.remove(id)
    fun clear() = box.clear()
    fun setDropHover(hover: Boolean) { dropHover.value = hover }

    fun open(item: BoxItem) {
        if (item.kind == ItemKind.URL) platform.openUrl(item.content.orEmpty()) else item.source?.let(platform::openFile)
    }
}
