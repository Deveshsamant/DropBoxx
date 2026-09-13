package com.dropnest.engine.transfer

import com.dropnest.core.AppInfo
import com.dropnest.core.nowMillis
import com.dropnest.domain.HistoryStore
import com.dropnest.domain.PlatformServices
import com.dropnest.model.ItemStatus
import com.dropnest.model.SessionStatus
import com.dropnest.model.TransferItem
import com.dropnest.model.TransferSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Single source of truth for live transfers.
 *
 * Byte counters are plain atomics bumped from the I/O threads; a 5 Hz ticker folds them into the
 * immutable [TransferSession] snapshots the UI observes. Status changes publish immediately.
 */
class SessionRegistry(
    private val history: HistoryStore,
    private val platform: PlatformServices,
    scope: CoroutineScope,
) {
    private class Live(
        @Volatile var session: TransferSession,
        val counters: ConcurrentHashMap<String, AtomicLong> = ConcurrentHashMap(),
        @Volatile var lastBytes: Long = 0,
        @Volatile var lastTick: Long = nowMillis(),
        @Volatile var speed: Double = 0.0,
        @Volatile var lastActivity: Long = nowMillis(),
    )

    private val live = ConcurrentHashMap<String, Live>()
    private val _sessions = MutableStateFlow<List<TransferSession>>(emptyList())
    val sessions: StateFlow<List<TransferSession>> get() = _sessions

    init {
        scope.launch {
            while (isActive) {
                delay(AppInfo.PROGRESS_TICK_MILLIS)
                if (live.values.any { it.session.status == SessionStatus.ACTIVE }) tick()
            }
        }
    }

    fun get(id: String): TransferSession? = live[id]?.session

    fun create(session: TransferSession): TransferSession {
        val l = Live(session)
        session.items.forEach { l.counters[it.id] = AtomicLong(it.bytesDone) }
        live[session.id] = l
        publish()
        updateKeepAwake()
        return session
    }

    fun update(id: String, block: (TransferSession) -> TransferSession) {
        val l = live[id] ?: return
        synchronized(l) { l.session = block(l.session) }
        publish()
    }

    fun updateItem(sessionId: String, itemId: String, block: (TransferItem) -> TransferItem) {
        update(sessionId) { s -> s.copy(items = s.items.map { if (it.id == itemId) block(it) else it }) }
    }

    fun addBytes(sessionId: String, itemId: String, delta: Long) {
        val l = live[sessionId] ?: return
        l.counters[itemId]?.addAndGet(delta)
        l.lastActivity = nowMillis()
    }

    fun setBytes(sessionId: String, itemId: String, value: Long) {
        val l = live[sessionId] ?: return
        l.counters[itemId]?.set(value)
        l.lastActivity = nowMillis()
    }

    fun lastActivity(sessionId: String): Long = live[sessionId]?.lastActivity ?: 0L

    /** Terminal transition: freezes counters, records history, releases wake locks. */
    fun finish(id: String, status: SessionStatus, error: String? = null) {
        val l = live[id] ?: return
        synchronized(l) {
            val s = fold(l)
            val finalItems = s.items.map {
                when {
                    it.status == ItemStatus.DONE || it.status == ItemStatus.FAILED || it.status == ItemStatus.SKIPPED -> it
                    status == SessionStatus.COMPLETED -> it.copy(status = ItemStatus.DONE, bytesDone = it.size)
                    else -> it.copy(status = ItemStatus.SKIPPED)
                }
            }
            l.session = s.copy(status = status, items = finalItems, finishedAt = nowMillis(), speedBps = 0, error = error)
        }
        publish()
        if (status != SessionStatus.DECLINED) history.add(l.session)
        updateKeepAwake()
    }

    fun remove(id: String) {
        live.remove(id)
        publish()
        updateKeepAwake()
    }

    fun clearFinished() {
        live.entries.removeIf { it.value.session.status.isTerminal }
        publish()
    }

    private fun tick() {
        for (l in live.values) {
            if (l.session.status != SessionStatus.ACTIVE) continue
            synchronized(l) { l.session = fold(l) }
        }
        publish()
    }

    /** Folds atomic counters into the snapshot and refreshes the speed estimate (EMA). */
    private fun fold(l: Live): TransferSession {
        val s = l.session
        val items = s.items.map { item ->
            val done = l.counters[item.id]?.get() ?: item.bytesDone
            if (done != item.bytesDone && item.status != ItemStatus.DONE) item.copy(bytesDone = done.coerceAtMost(item.size)) else item
        }
        val bytesDone = items.sumOf { it.bytesDone }
        val now = nowMillis()
        val dt = (now - l.lastTick).coerceAtLeast(1)
        if (dt >= AppInfo.PROGRESS_TICK_MILLIS) {
            val instant = (bytesDone - l.lastBytes) * 1000.0 / dt
            l.speed = if (l.speed == 0.0) instant else l.speed * 0.7 + instant * 0.3
            l.lastBytes = bytesDone
            l.lastTick = now
        }
        return s.copy(items = items, bytesDone = bytesDone, speedBps = l.speed.toLong().coerceAtLeast(0))
    }

    private fun publish() {
        _sessions.value = live.values.map { it.session }.sortedByDescending { it.startedAt }
    }

    private fun updateKeepAwake() {
        platform.setKeepAwake(live.values.any { !it.session.status.isTerminal })
    }
}
