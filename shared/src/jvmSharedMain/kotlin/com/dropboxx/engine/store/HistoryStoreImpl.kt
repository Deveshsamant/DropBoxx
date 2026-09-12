package com.dropboxx.engine.store

import com.dropboxx.domain.HistoryStore
import com.dropboxx.model.DeviceInfo
import com.dropboxx.model.Direction
import com.dropboxx.model.ItemKind
import com.dropboxx.model.ItemStatus
import com.dropboxx.model.SessionStatus
import com.dropboxx.model.TransferItem
import com.dropboxx.model.TransferSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
private data class HistoryItemDto(
    val id: String, val name: String, val size: Long, val mimeType: String, val kind: ItemKind,
    val status: ItemStatus, val resultPath: String? = null, val content: String? = null, val error: String? = null,
)

@Serializable
private data class HistoryEntryDto(
    val id: String, val direction: Direction, val peer: DeviceInfo, val peerAddress: String,
    val items: List<HistoryItemDto>, val status: SessionStatus, val startedAt: Long, val finishedAt: Long? = null,
    val bytesTotal: Long, val error: String? = null,
)

@Serializable
private data class HistoryFile(val entries: List<HistoryEntryDto> = emptyList())

class HistoryStoreImpl(dataDirectory: String, private val maxEntries: Int = 500) : HistoryStore {

    private val store = JsonFileStore(File(dataDirectory, "history.json"), HistoryFile.serializer()) { HistoryFile() }
    private var data = store.load()

    private val _entries = MutableStateFlow(data.entries.map { it.toModel() })
    override val entries: StateFlow<List<TransferSession>> get() = _entries

    @Synchronized
    override fun add(session: TransferSession) {
        val dto = session.toDto()
        data = data.copy(entries = (listOf(dto) + data.entries.filter { it.id != dto.id }).take(maxEntries))
        commit()
    }

    @Synchronized
    override fun remove(id: String) {
        data = data.copy(entries = data.entries.filter { it.id != id })
        commit()
    }

    @Synchronized
    override fun clear() {
        data = HistoryFile()
        commit()
    }

    private fun commit() {
        store.save(data)
        _entries.value = data.entries.map { it.toModel() }
    }

    private fun TransferSession.toDto() = HistoryEntryDto(
        id, direction, peer, peerAddress,
        items.map { HistoryItemDto(it.id, it.name, it.size, it.mimeType, it.kind, it.status, it.resultPath, it.content, it.error) },
        status, startedAt, finishedAt, bytesTotal, error,
    )

    private fun HistoryEntryDto.toModel() = TransferSession(
        id = id, direction = direction, peer = peer, peerAddress = peerAddress,
        items = items.map { TransferItem(it.id, it.name, it.size, it.mimeType, it.kind, if (it.status == ItemStatus.DONE) it.size else 0, it.status, it.resultPath, it.content, it.error) },
        status = status, startedAt = startedAt, finishedAt = finishedAt,
        bytesDone = items.filter { it.status == ItemStatus.DONE }.sumOf { it.size }, bytesTotal = bytesTotal, error = error,
    )
}
