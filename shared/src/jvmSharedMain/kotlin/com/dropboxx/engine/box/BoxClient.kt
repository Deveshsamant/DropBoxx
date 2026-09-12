package com.dropboxx.engine.box

import co.touchlab.kermit.Logger
import com.dropboxx.core.AppInfo
import com.dropboxx.core.nowMillis
import com.dropboxx.core.randomId
import com.dropboxx.domain.BrowseOutcome
import com.dropboxx.domain.PlatformServices
import com.dropboxx.domain.TrustStore
import com.dropboxx.engine.identity.IdentityManager
import com.dropboxx.engine.net.PeerClients
import com.dropboxx.engine.transfer.SessionRegistry
import com.dropboxx.model.Api
import com.dropboxx.model.BoxEntry
import com.dropboxx.model.BoxListRequest
import com.dropboxx.model.BoxListResponse
import com.dropboxx.model.Direction
import com.dropboxx.model.ItemKind
import com.dropboxx.model.ItemStatus
import com.dropboxx.model.Peer
import com.dropboxx.model.ReceivedContent
import com.dropboxx.model.SessionStatus
import com.dropboxx.model.TransferItem
import com.dropboxx.model.TransferSession
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okio.buffer
import java.util.concurrent.ConcurrentHashMap

/** Requester side of the box: list a peer's items and pull them down with resume. */
class BoxClient(
    private val registry: SessionRegistry,
    private val clients: PeerClients,
    private val trustStore: TrustStore,
    private val identity: IdentityManager,
    private val platform: PlatformServices,
    private val scope: CoroutineScope,
    /** Shared with ReceiveController so the UI observes one stream for pushed and pulled text. */
    private val receivedContent: MutableSharedFlow<ReceivedContent>,
) {
    private val log = Logger.withTag("BoxClient")
    private val jobs = ConcurrentHashMap<String, Job>()

    suspend fun browse(peer: Peer, pin: String?): BrowseOutcome {
        val client = clients.forFingerprint(peer.info.fingerprint)
        val response = try {
            client.post("${peer.baseUrl}${Api.BOX_LIST}") {
                contentType(ContentType.Application.Json)
                setBody(BoxListRequest(identity.info.value, trustStore.outgoingToken(peer.id), pin))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return BrowseOutcome.Failed(e.message ?: "Could not reach ${peer.info.alias}")
        }
        return when (response.status) {
            HttpStatusCode.OK -> {
                val body: BoxListResponse = response.body()
                body.pairToken?.let { trustStore.saveOutgoingToken(peer.id, it) }
                BrowseOutcome.Ok(body.items, body.accessToken)
            }
            HttpStatusCode.Unauthorized -> BrowseOutcome.PinRequired
            HttpStatusCode.Forbidden -> BrowseOutcome.Denied
            HttpStatusCode.Conflict -> BrowseOutcome.Busy
            else -> BrowseOutcome.Failed("Peer answered ${response.status}")
        }
    }

    fun download(peer: Peer, entries: List<BoxEntry>, accessToken: String): String {
        val sessionId = randomId()
        val items = entries.map { TransferItem(it.id, it.name, it.size, it.mimeType, it.kind, content = it.content) }
        registry.create(
            TransferSession(
                id = sessionId, direction = Direction.RECEIVE, peer = peer.info, peerAddress = peer.address,
                items = items.map { if (it.kind == ItemKind.FILE) it else it.copy(status = ItemStatus.DONE, bytesDone = it.size) },
                status = SessionStatus.ACTIVE, startedAt = nowMillis(), bytesTotal = items.sumOf { it.size },
            )
        )
        entries.filter { it.kind != ItemKind.FILE }.forEach { receivedContent.tryEmit(ReceivedContent(it.kind, it.content.orEmpty(), peer.info)) }
        val files = entries.filter { it.kind == ItemKind.FILE }
        if (files.isEmpty()) {
            registry.finish(sessionId, SessionStatus.COMPLETED)
            return sessionId
        }
        jobs[sessionId] = scope.launch(Dispatchers.IO) { downloadAll(sessionId, peer, files, accessToken) }
        return sessionId
    }

    fun cancel(sessionId: String): Boolean {
        val job = jobs.remove(sessionId) ?: return false
        job.cancel()
        registry.finish(sessionId, SessionStatus.CANCELLED)
        return true
    }

    fun isDownloading(sessionId: String) = jobs.containsKey(sessionId)

    private suspend fun downloadAll(sessionId: String, peer: Peer, files: List<BoxEntry>, token: String) {
        val client = clients.forFingerprint(peer.info.fingerprint)
        val gate = Semaphore(AppInfo.PARALLEL_UPLOADS)
        try {
            coroutineScope {
                for (entry in files) launch {
                    gate.withPermit {
                        val result = fetchWithRetry(sessionId, peer, client, entry, token)
                        registry.updateItem(sessionId, entry.id) {
                            if (result == null) it.copy(status = ItemStatus.FAILED, error = "Download failed")
                            else it.copy(status = ItemStatus.DONE, bytesDone = it.size, resultPath = result, error = null)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            return
        }
        jobs.remove(sessionId)
        val failed = registry.get(sessionId)?.items.orEmpty().count { it.status == ItemStatus.FAILED }
        registry.finish(sessionId, if (failed == 0) SessionStatus.COMPLETED else SessionStatus.COMPLETED_WITH_ERRORS)
        if (failed == 0) platform.notify("Downloaded ${files.size} item${if (files.size == 1) "" else "s"} from ${peer.info.alias}", platform.receiveStorage.saveDirectoryDisplay)
    }

    /** Streams one entry to [platform.receiveStorage], resuming with Range after a dropped connection. Returns the saved path. */
    private suspend fun fetchWithRetry(sessionId: String, peer: Peer, client: io.ktor.client.HttpClient, entry: BoxEntry, token: String): String? {
        val target = runCatching { platform.receiveStorage.open(entry.name, entry.mimeType, entry.size) }
            .getOrElse { log.w { "cannot create ${entry.name}: ${it.message}" }; return null }
        repeat(MAX_ATTEMPTS) { attempt ->
            val offset = target.bytesWritten
            registry.updateItem(sessionId, entry.id) { it.copy(status = ItemStatus.ACTIVE, error = null) }
            registry.setBytes(sessionId, entry.id, offset)
            try {
                val outcome = client.prepareGet("${peer.baseUrl}${Api.BOX_ITEM}/${entry.id}") {
                    parameter("token", token)
                    if (offset > 0) header("Range", "bytes=$offset-")
                }.execute { response ->
                    when (response.status) {
                        HttpStatusCode.OK, HttpStatusCode.PartialContent -> Unit
                        HttpStatusCode.NotFound, HttpStatusCode.Gone, HttpStatusCode.Forbidden -> return@execute GONE
                        else -> throw java.io.IOException("peer answered ${response.status}")
                    }
                    val start = if (response.status == HttpStatusCode.PartialContent) offset else 0L
                    if (start != target.bytesWritten) throw java.io.IOException("resume offset mismatch")
                    val channel = response.bodyAsChannel()
                    target.sink().buffer().use { sink ->
                        val buf = ByteArray(AppInfo.IO_BUFFER_SIZE)
                        var received = start
                        while (received < entry.size) {
                            val n = channel.readAvailable(buf, 0, minOf(buf.size.toLong(), entry.size - received).toInt())
                            if (n == -1) break
                            if (n == 0) continue
                            sink.write(buf, 0, n)
                            received += n
                            registry.addBytes(sessionId, entry.id, n.toLong())
                        }
                        sink.flush()
                        if (received == entry.size) DONE else INCOMPLETE
                    }
                }
                when (outcome) {
                    DONE -> return target.complete()
                    GONE -> { runCatching { target.abort() }; return null }
                    else -> log.w { "download ${entry.name} ended early at ${target.bytesWritten}/${entry.size}" }
                }
            } catch (e: CancellationException) {
                runCatching { target.abort() }
                throw e
            } catch (e: Exception) {
                log.w { "download ${entry.name} attempt ${attempt + 1} failed: ${e.message}" }
            }
            delay(RETRY_DELAY_MILLIS * (attempt + 1))
        }
        runCatching { target.abort() }
        return null
    }

    private companion object {
        const val MAX_ATTEMPTS = 4
        const val RETRY_DELAY_MILLIS = 1_000L
        const val DONE = 1
        const val INCOMPLETE = 0
        const val GONE = -1
    }
}
