package com.dropboxx.ui.transfers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dropboxx.domain.HistoryStore
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.TransferEngine
import com.dropboxx.model.TransferSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TransfersUiState(
    val live: List<TransferSession> = emptyList(),
    val history: List<TransferSession> = emptyList(),
)

class TransfersViewModel(
    private val engine: TransferEngine,
    private val history: HistoryStore,
    private val platform: PlatformServices,
) : ViewModel() {

    val state: StateFlow<TransfersUiState> = combine(engine.sessions, history.entries) { live, hist ->
        val liveIds = live.map { it.id }.toSet()
        TransfersUiState(live = live, history = hist.filterNot { it.id in liveIds })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransfersUiState())

    fun cancel(id: String) = engine.cancel(id)
    fun dismiss(id: String) = engine.remove(id)
    fun clearFinished() = engine.clearFinished()
    fun removeHistory(id: String) = history.remove(id)
    fun clearHistory() = history.clear()
    fun open(path: String) = platform.openFile(path)
    fun reveal(path: String) = platform.revealFile(path)
    fun openUrl(url: String) = platform.openUrl(url)
}
