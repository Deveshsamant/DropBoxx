package com.dropboxx.engine.transfer

import co.touchlab.kermit.Logger
import com.dropboxx.core.AppInfo
import com.dropboxx.core.nowMillis
import com.dropboxx.core.randomId
import com.dropboxx.core.secureToken
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.ReceiveTarget
import com.dropboxx.domain.TrustStore
import com.dropboxx.model.DeviceInfo
import com.dropboxx.model.Direction
import com.dropboxx.model.FileMeta
import com.dropboxx.model.IncomingDecision
import com.dropboxx.model.IncomingRequest
import com.dropboxx.model.ItemKind
import com.dropboxx.model.ItemStatus
import com.dropboxx.model.PrepareUploadRequest
import com.dropboxx.model.PrepareUploadResponse
import com.dropboxx.model.ReceivedContent
import com.dropboxx.model.SessionStatus
import com.dropboxx.model.TransferItem
import com.dropboxx.model.TransferSession
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

sealed interface PrepareResult {
    data class Ok(val response: PrepareUploadResponse) : PrepareResult
    data object Declined : PrepareResult
    data object PinRequired : PrepareResult
    data object Busy : PrepareResult
    data class BadRequest(val message: String) : PrepareResult
}

sealed interface UploadResult {
    data object Ok : UploadResult
    data object NotFound : UploadResult
    data class OffsetMismatch(val current: Long) : UploadResult
    data class Failed(val message: String) : UploadResult
}

/** Receiver half of the protocol: accept dialog, tokens, streaming to disk, resume, watchdog. */
class ReceiveController(
    private val registry: SessionRegistry,
    private val trustStore: TrustStore,
    private val settings: AppSettings,
    private val platform: PlatformServices,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("Receive")

    val incomingRequest = MutableStateFlow<IncomingRequest?>(null)
    val receivedContent = MutableSharedFlow<ReceivedContent>(extraBufferCapacity = 32)

    private class Session(
        val id: String,
        val sender: DeviceInfo,
        val tokens: Map<String, String>,
        val metas: Map<String, FileMeta>,
    ) {
        val targets = ConcurrentHashMap<String, ReceiveTarget>()
        val locks = ConcurrentHashMap<String, Mutex>()
        val done = ConcurrentHashMap.newKeySet<String>()
        val failed = ConcurrentHashMap.newKeySet<String>()
    }

    private val sessions = ConcurrentHashMap<String, Session>()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<IncomingDecision>>()

    init {
        scope.launch { watchdog() }
    }

    suspend fun prepare(req: PrepareUploadRequest, remoteAddress: String): PrepareResult {
        if (req.files.isEmpty()) return PrepareResult.BadRequest("no items")
        for (f in req.files.values) {
            if (f.kind != ItemKind.FILE && (f.content == null || f.content.length > AppInfo.MAX_INLINE_TEXT_BYTES)) {
                return PrepareResult.BadRequest("inline content missing or too large")
            }
        }
        val pin = settings.current.pin
        if (pin.isNotEmpty() && req.pin != pin) return PrepareResult.PinRequired

        val trusted = trustStore.find(req.info.id)?.takeIf { it.fingerprint == req.info.fingerprint && it.pairToken == req.pairToken }
        val items = req.files.values.map { TransferItem(it.id, it.fileName, it.size, it.fileType, it.kind, content = it.content) }
        val total = items.sumOf { it.size }

        val decision = if (trusted != null && settings.current.quickSave) {
            IncomingDecision(accept = true)
        } else {
            if (incomingRequest.value != null) return PrepareResult.Busy
            val requestId = randomId()
            val deferred = CompletableDeferred<IncomingDecision>()
            pending[requestId] = deferred
            incomingRequest.value = IncomingRequest(requestId, req.info, remoteAddress, items, total, nowMillis())
            platform.notify("${req.info.alias} wants to send you ${describe(items)}", "Open DropBoxx to accept")
            try {
                withTimeoutOrNull(AppInfo.ACCEPT_TIMEOUT_MILLIS) { deferred.await() } ?: IncomingDecision(accept = false)
            } finally {
                pending.remove(requestId)
                if (incomingRequest.value?.sessionId == requestId) incomingRequest.value = null
            }
        }
        if (!decision.accept) return PrepareResult.Declined

        val sessionId = randomId()
        val tokens = req.files.values.filter { it.kind == ItemKind.FILE }.associate { it.id to secureToken(16) }
        val session = Session(sessionId, req.info, tokens, req.files)
        sessions[sessionId] = session

        registry.create(
            TransferSession(
                id = sessionId, direction = Direction.RECEIVE, peer = req.info, peerAddress = remoteAddress,
                items = items.map { if (it.kind == ItemKind.FILE) it else it.copy(status = ItemStatus.DONE, bytesDone = it.size) },
                status = SessionStatus.ACTIVE, startedAt = nowMillis(), bytesTotal = total,
            )
        )
        items.filter { it.kind != ItemKind.FILE }.forEach { item ->
            session.done += item.id
            receivedContent.tryEmit(ReceivedContent(item.kind, item.content.orEmpty(), req.info))
        }

        var grantedToken: String? = null
        if (decision.trustSender) {
            grantedToken = secureToken()
            trustStore.trust(req.info, grantedToken)
        }
        if (tokens.isEmpty()) completeIfDone(session)
        return PrepareResult.Ok(PrepareUploadResponse(sessionId, tokens, grantedToken))
    }

    suspend fun upload(sessionId: String, fileId: String, token: String, offset: Long?, channel: ByteReadChannel): UploadResult {
        val session = sessions[sessionId] ?: return UploadResult.NotFound
        if (session.tokens[fileId] != token) return UploadResult.NotFound
        val meta = session.metas[fileId] ?: return UploadResult.NotFound
        if (fileId in session.done) return UploadResult.Ok

        val lock = session.locks.getOrPut(fileId) { Mutex() }
        return lock.withLock {
            val target = session.targets[fileId] ?: runCatching { platform.receiveStorage.open(meta.fileName, meta.fileType, meta.size) }
                .getOrElse { return@withLock UploadResult.Failed("cannot create file: ${it.message}") }
                .also { session.targets[fileId] = it }

            val start = offset ?: 0L
            if (start != target.bytesWritten) return@withLock UploadResult.OffsetMismatch(target.bytesWritten)

            registry.updateItem(sessionId, fileId) { it.copy(status = ItemStatus.ACTIVE, error = null) }
            registry.setBytes(sessionId, fileId, start)

            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    target.sink().buffer().use { sink ->
                        val buf = ByteArray(AppInfo.IO_BUFFER_SIZE)
                        var received = start
                        while (received < meta.size) {
                            val n = channel.readAvailable(buf, 0, minOf(buf.size.toLong(), meta.size - received).toInt())
                            if (n == -1) break
                            if (n == 0) continue
                            sink.write(buf, 0, n)
                            received += n
                            registry.addBytes(sessionId, fileId, n.toLong())
                        }
                        sink.flush()
                        received
                    }
                }
            }
            val received = outcome.getOrElse { e ->
                log.w { "upload ${meta.fileName} interrupted: ${e.message}" }
                registry.updateItem(sessionId, fileId) { it.copy(error = "Interrupted, waiting for sender to resume") }
                return@withLock UploadResult.Failed(e.message ?: "stream error")
            }
            if (received < meta.size) {
                registry.updateItem(sessionId, fileId) { it.copy(error = "Interrupted, waiting for sender to resume") }
                return@withLock UploadResult.Failed("incomplete: $received of ${meta.size}")
            }
            val path = runCatching { target.complete() }.getOrElse { e ->
                session.failed += fileId
                registry.updateItem(sessionId, fileId) { it.copy(status = ItemStatus.FAILED, error = e.message) }
                completeIfDone(session)
                return@withLock UploadResult.Failed("finalize failed: ${e.message}")
            }
            session.targets.remove(fileId)
            session.done += fileId
            registry.updateItem(sessionId, fileId) { it.copy(status = ItemStatus.DONE, bytesDone = it.size, resultPath = path, error = null) }
            completeIfDone(session)
            UploadResult.Ok
        }
    }

    fun status(sessionId: String, fileId: String, token: String): Long? {
        val session = sessions[sessionId] ?: return null
        if (session.tokens[fileId] != token) return null
        if (fileId in session.done) return session.metas[fileId]?.size
        return session.targets[fileId]?.bytesWritten ?: 0L
    }

    fun respond(requestId: String, decision: IncomingDecision) {
        pending[requestId]?.complete(decision)
    }

    /** Sender gave up, or the local user hit cancel. */
    fun cancel(sessionId: String, byRemote: Boolean) {
        val session = sessions.remove(sessionId) ?: return
        scope.launch { session.targets.values.forEach { runCatching { it.abort() } } }
        registry.finish(sessionId, SessionStatus.CANCELLED, if (byRemote) "Cancelled by ${session.sender.alias}" else null)
    }

    fun isActive(sessionId: String) = sessions.containsKey(sessionId)

    private fun completeIfDone(session: Session) {
        val fileIds = session.tokens.keys
        if (!fileIds.all { it in session.done || it in session.failed }) return
        sessions.remove(session.id)
        val status = if (session.failed.isEmpty()) SessionStatus.COMPLETED else SessionStatus.COMPLETED_WITH_ERRORS
        registry.finish(session.id, status)
        val items = registry.get(session.id)?.items.orEmpty()
        val files = items.count { it.kind == ItemKind.FILE }
        if (files > 0) platform.notify("Received ${describe(items)} from ${session.sender.alias}", platform.receiveStorage.saveDirectoryDisplay)
    }

    /** Fails sessions whose sender vanished mid-transfer so they do not sit in the list forever. */
    private suspend fun watchdog() {
        while (scope.isActive) {
            delay(15_000)
            val cutoff = nowMillis() - 120_000
            for ((id, session) in sessions) {
                val s = registry.get(id) ?: continue
                if (s.status == SessionStatus.ACTIVE && registry.lastActivity(id) < cutoff) {
                    log.w { "Session $id from ${session.sender.alias} timed out" }
                    sessions.remove(id)
                    session.targets.values.forEach { runCatching { it.abort() } }
                    registry.finish(id, SessionStatus.FAILED, "Connection to ${session.sender.alias} was lost")
                }
            }
        }
    }

    private fun describe(items: List<TransferItem>): String {
        val files = items.count { it.kind == ItemKind.FILE }
        val texts = items.size - files
        return when {
            files > 0 && texts > 0 -> "$files file(s) and $texts message(s)"
            files == 1 -> items.first { it.kind == ItemKind.FILE }.name
            files > 1 -> "$files files"
            texts == 1 -> if (items.first().kind == ItemKind.URL) "a link" else "a message"
            else -> "$texts messages"
        }
    }
}
