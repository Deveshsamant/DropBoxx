package com.dropnest.engine.transfer

import co.touchlab.kermit.Logger
import com.dropnest.core.AppInfo
import com.dropnest.core.nowMillis
import com.dropnest.core.randomId
import com.dropnest.domain.SendOutcome
import com.dropnest.domain.TrustStore
import com.dropnest.engine.identity.IdentityManager
import com.dropnest.engine.net.PeerClients
import com.dropnest.model.Api
import com.dropnest.model.Direction
import com.dropnest.model.FileMeta
import com.dropnest.model.ItemKind
import com.dropnest.model.ItemStatus
import com.dropnest.model.OutgoingItem
import com.dropnest.model.Peer
import com.dropnest.model.PlatformFile
import com.dropnest.model.PrepareUploadRequest
import com.dropnest.model.PrepareUploadResponse
import com.dropnest.model.SessionStatus
import com.dropnest.model.TransferItem
import com.dropnest.model.TransferSession
import com.dropnest.model.UploadStatus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/** Sender half of the protocol: prepare, parallel streamed uploads with resume, cancel. */
class SendController(
    private val registry: SessionRegistry,
    private val clients: PeerClients,
    private val trustStore: TrustStore,
    private val identity: IdentityManager,
    private val scope: CoroutineScope,
) {
    private val log = Logger.withTag("Send")

    private class Outgoing(val remoteSessionId: String, val peer: Peer, val job: Job)

    private val outgoing = ConcurrentHashMap<String, Outgoing>()

    suspend fun send(peer: Peer, items: List<OutgoingItem>, pin: String?): SendOutcome {
        if (items.isEmpty()) return SendOutcome.Failed("Nothing to send")
        val metas = items.associate { item ->
            item.id to when (item) {
                is OutgoingItem.File -> FileMeta(item.id, item.file.name, item.file.size, item.file.mimeType, ItemKind.FILE)
                is OutgoingItem.Text -> FileMeta(item.id, "message.txt", item.size, "text/plain", ItemKind.TEXT, item.text)
                is OutgoingItem.Url -> FileMeta(item.id, "link.url", item.size, "text/uri-list", ItemKind.URL, item.url)
            }
        }
        val localId = randomId()
        val session = TransferSession(
            id = localId, direction = Direction.SEND, peer = peer.info, peerAddress = peer.address,
            items = metas.values.map { TransferItem(it.id, it.fileName, it.size, it.fileType, it.kind, content = it.content) },
            status = SessionStatus.PENDING, startedAt = nowMillis(), bytesTotal = metas.values.sumOf { it.size },
        )
        registry.create(session)

        val client = clients.forFingerprint(peer.info.fingerprint)
        val request = PrepareUploadRequest(identity.info.value, metas, pin, trustStore.outgoingToken(peer.id))
        val response = try {
            client.post("${peer.baseUrl}${Api.PREPARE_UPLOAD}") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.w { "prepare to ${peer.info.alias} failed: ${e.message}" }
            registry.finish(localId, SessionStatus.FAILED, friendly(e))
            return SendOutcome.Failed(friendly(e))
        }
        when (response.status) {
            HttpStatusCode.OK -> Unit
            HttpStatusCode.Unauthorized -> { registry.remove(localId); return SendOutcome.PinRequired }
            HttpStatusCode.Forbidden -> { registry.finish(localId, SessionStatus.DECLINED, "${peer.info.alias} declined"); return SendOutcome.Declined }
            HttpStatusCode.Conflict -> { registry.remove(localId); return SendOutcome.Busy }
            else -> { registry.finish(localId, SessionStatus.FAILED, "Peer answered ${response.status}"); return SendOutcome.Failed("Peer answered ${response.status}") }
        }
        val prepared: PrepareUploadResponse = response.body()
        prepared.pairToken?.let { trustStore.saveOutgoingToken(peer.id, it) }

        registry.update(localId) { s ->
            s.copy(status = SessionStatus.ACTIVE, items = s.items.map { if (it.kind == ItemKind.FILE) it else it.copy(status = ItemStatus.DONE, bytesDone = it.size) })
        }
        val files = items.filterIsInstance<OutgoingItem.File>().filter { prepared.files.containsKey(it.id) }
        if (files.isEmpty()) {
            registry.finish(localId, SessionStatus.COMPLETED)
            return SendOutcome.Started
        }
        val job = scope.launch(Dispatchers.IO) { uploadAll(localId, prepared, peer, client, files) }
        outgoing[localId] = Outgoing(prepared.sessionId, peer, job)
        return SendOutcome.Started
    }

    fun cancel(localId: String) {
        val o = outgoing.remove(localId) ?: return
        o.job.cancel()
        registry.finish(localId, SessionStatus.CANCELLED)
        scope.launch(Dispatchers.IO) {
            runCatching {
                clients.forFingerprint(o.peer.info.fingerprint).post("${o.peer.baseUrl}${Api.CANCEL}") { parameter("sessionId", o.remoteSessionId) }
            }
        }
    }

    fun isOutgoing(localId: String) = outgoing.containsKey(localId)

    private suspend fun uploadAll(localId: String, prepared: PrepareUploadResponse, peer: Peer, client: HttpClient, files: List<OutgoingItem.File>) {
        val gate = Semaphore(AppInfo.PARALLEL_UPLOADS)
        var cancelledByPeer = false
        try {
            coroutineScope {
                for (item in files) {
                    launch {
                        gate.withPermit {
                            val token = prepared.files.getValue(item.id)
                            val result = uploadWithRetry(localId, prepared.sessionId, peer, client, item.id, token, item.file)
                            when (result) {
                                FileResult.Done -> registry.updateItem(localId, item.id) { it.copy(status = ItemStatus.DONE, bytesDone = it.size, error = null) }
                                FileResult.Gone -> { cancelledByPeer = true; this@coroutineScope.cancel() }
                                is FileResult.Failed -> registry.updateItem(localId, item.id) { it.copy(status = ItemStatus.FAILED, error = result.message) }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            if (!cancelledByPeer && outgoing.containsKey(localId).not()) return  // local cancel already finished the session
        }
        outgoing.remove(localId)
        if (cancelledByPeer) {
            registry.finish(localId, SessionStatus.CANCELLED, "${peer.info.alias} cancelled")
            return
        }
        val failed = registry.get(localId)?.items.orEmpty().count { it.status == ItemStatus.FAILED }
        registry.finish(localId, if (failed == 0) SessionStatus.COMPLETED else SessionStatus.COMPLETED_WITH_ERRORS)
    }

    private sealed interface FileResult {
        data object Done : FileResult
        data object Gone : FileResult
        data class Failed(val message: String) : FileResult
    }

    private suspend fun uploadWithRetry(localId: String, remoteId: String, peer: Peer, client: HttpClient, fileId: String, token: String, file: PlatformFile): FileResult {
        var lastError = "unknown error"
        repeat(MAX_ATTEMPTS) { attempt ->
            val offset = if (attempt == 0) 0L else {
                val status = runCatching {
                    val r = client.get("${peer.baseUrl}${Api.UPLOAD_STATUS}") {
                        parameter("sessionId", remoteId); parameter("fileId", fileId); parameter("token", token)
                    }
                    if (r.status == HttpStatusCode.OK) r.body<UploadStatus>().bytesReceived else if (r.status == HttpStatusCode.NotFound) return FileResult.Gone else 0L
                }.getOrElse { 0L }
                status.coerceIn(0L, file.size)
            }
            if (offset >= file.size) return FileResult.Done

            registry.updateItem(localId, fileId) { it.copy(status = ItemStatus.ACTIVE, error = null) }
            registry.setBytes(localId, fileId, offset)
            try {
                val response = client.post("${peer.baseUrl}${Api.UPLOAD}") {
                    parameter("sessionId", remoteId); parameter("fileId", fileId); parameter("token", token)
                    header(Api.HEADER_OFFSET, offset.toString())
                    setBody(StreamingBody(file, offset) { n -> registry.addBytes(localId, fileId, n) })
                }
                when (response.status) {
                    HttpStatusCode.OK -> return FileResult.Done
                    HttpStatusCode.NotFound, HttpStatusCode.Gone -> return FileResult.Gone
                    HttpStatusCode.Conflict -> lastError = "offset mismatch"
                    else -> lastError = "peer answered ${response.status}"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = friendly(e)
                log.w { "upload ${file.name} attempt ${attempt + 1} failed: $lastError" }
            }
            delay(RETRY_DELAY_MILLIS * (attempt + 1))
        }
        return FileResult.Failed(lastError)
    }

    /** Streams straight from the source to the socket; never holds more than one buffer in memory. */
    private class StreamingBody(private val file: PlatformFile, private val offset: Long, private val onBytes: (Long) -> Unit) : OutgoingContent.WriteChannelContent() {
        override val contentLength: Long = file.size - offset
        override val contentType: ContentType = ContentType.Application.OctetStream

        override suspend fun writeTo(channel: ByteWriteChannel) {
            withContext(Dispatchers.IO) {
                file.open().buffer().use { source ->
                    if (offset > 0) source.skip(offset)
                    val buf = ByteArray(AppInfo.IO_BUFFER_SIZE)
                    var remaining = contentLength
                    while (remaining > 0) {
                        val n = source.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                        if (n == -1) break
                        channel.writeFully(buf, 0, n)
                        remaining -= n
                        onBytes(n.toLong())
                    }
                    channel.flush()
                }
            }
        }
    }

    private fun friendly(e: Throwable): String = when {
        e.message?.contains("fingerprint mismatch") == true -> "Peer identity changed - remove and re-discover it"
        e is java.net.ConnectException -> "Could not connect (firewall or peer offline)"
        e is java.net.SocketTimeoutException -> "Connection timed out"
        e is java.io.IOException -> "Connection lost"
        else -> e.message ?: e::class.simpleName ?: "error"
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        const val RETRY_DELAY_MILLIS = 1_000L
    }
}
