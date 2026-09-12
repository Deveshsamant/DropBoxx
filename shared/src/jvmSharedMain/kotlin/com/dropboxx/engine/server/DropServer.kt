package com.dropboxx.engine.server

import co.touchlab.kermit.Logger
import com.dropboxx.core.nowMillis
import com.dropboxx.domain.AppSettings
import com.dropboxx.domain.DiscoveryService
import com.dropboxx.domain.LocalServer
import com.dropboxx.domain.ServerState
import com.dropboxx.engine.identity.IdentityManager
import com.dropboxx.engine.net.NetworkUtils
import com.dropboxx.engine.net.ProtocolJson
import com.dropboxx.engine.box.BoxAccessController
import com.dropboxx.engine.box.ListResult
import com.dropboxx.engine.transfer.PrepareResult
import com.dropboxx.engine.transfer.ReceiveController
import com.dropboxx.engine.transfer.UploadResult
import com.dropboxx.model.Api
import com.dropboxx.model.BoxListRequest
import com.dropboxx.model.DeviceInfo
import com.dropboxx.model.ErrorResponse
import com.dropboxx.model.Peer
import com.dropboxx.model.PrepareUploadRequest
import com.dropboxx.model.UploadStatus
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.writeFully
import okio.buffer
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The HTTPS endpoint every peer talks to. One per device, bound on all interfaces. */
class DropServer(
    private val identity: IdentityManager,
    private val settings: AppSettings,
    private val discovery: DiscoveryService,
    private val receive: ReceiveController,
    private val boxAccess: BoxAccessController,
    private val scope: CoroutineScope,
) : LocalServer {

    private val log = Logger.withTag("Server")
    private val _state = MutableStateFlow<ServerState>(ServerState.Stopped)
    override val state: StateFlow<ServerState> get() = _state

    private var engine: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private val lifecycle = Mutex()

    override fun start() {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { startLocked() } }
    }

    override fun stop() {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { stopLocked() } }
    }

    override fun restart() {
        scope.launch(Dispatchers.IO) { lifecycle.withLock { stopLocked(); startLocked() } }
    }

    private suspend fun startLocked() {
        if (engine != null) return
        _state.value = ServerState.Starting
        val preferred = settings.current.port
        for (port in listOf(preferred, 0)) {
            val server = build(port)
            try {
                server.start(wait = false)
                val bound = server.engine.resolvedConnectors().first().port
                engine = server
                identity.updateBoundPort(bound)
                _state.value = ServerState.Running(bound, NetworkUtils.localIpv4().map { it.address })
                log.i { "Listening on https://0.0.0.0:$bound" }
                discovery.start()
                discovery.announce()
                return
            } catch (e: Exception) {
                log.w { "Could not bind port $port: ${e.message}" }
                runCatching { server.stop(0, 500) }
            }
        }
        _state.value = ServerState.Failed("Could not open a network port")
    }

    private fun stopLocked() {
        discovery.stop()
        engine?.let { runCatching { it.stop(300, 1_000) } }
        engine = null
        _state.value = ServerState.Stopped
    }

    private fun build(port: Int) = embeddedServer(
        Netty,
        configure = {
            sslConnector(
                keyStore = identity.keyStore,
                keyAlias = IdentityManager.ALIAS,
                keyStorePassword = { IdentityManager.PASSWORD.toCharArray() },
                privateKeyPassword = { IdentityManager.PASSWORD.toCharArray() },
            ) {
                this.port = port
                host = "0.0.0.0"
            }
        },
        module = { module() },
    )

    private fun Application.module() {
        install(ContentNegotiation) { json(ProtocolJson) }
        install(StatusPages) {
            exception<Throwable> { call, cause ->
                log.w { "Unhandled ${cause::class.simpleName}: ${cause.message}" }
                call.respond(HttpStatusCode.InternalServerError, ErrorResponse(cause.message ?: "error"))
            }
        }
        routing {
            get(Api.INFO) { call.respond(identity.info.value) }

            post(Api.REGISTER) {
                val info = call.receive<DeviceInfo>()
                discovery.upsert(Peer(info, remoteIp(call.request.origin.remoteAddress), nowMillis()))
                call.respond(identity.info.value)
            }

            post(Api.PREPARE_UPLOAD) {
                val req = call.receive<PrepareUploadRequest>()
                val remote = remoteIp(call.request.origin.remoteAddress)
                discovery.upsert(Peer(req.info, remote, nowMillis()))
                when (val r = receive.prepare(req, remote)) {
                    is PrepareResult.Ok -> call.respond(r.response)
                    PrepareResult.Declined -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("declined"))
                    PrepareResult.PinRequired -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse("pin required"))
                    PrepareResult.Busy -> call.respond(HttpStatusCode.Conflict, ErrorResponse("busy"))
                    is PrepareResult.BadRequest -> call.respond(HttpStatusCode.BadRequest, ErrorResponse(r.message))
                }
            }

            post(Api.UPLOAD) {
                val q = call.request.queryParameters
                val sessionId = q["sessionId"]; val fileId = q["fileId"]; val token = q["token"]
                if (sessionId == null || fileId == null || token == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("missing parameters")); return@post
                }
                val offset = call.request.headers[Api.HEADER_OFFSET]?.toLongOrNull()
                when (val r = receive.upload(sessionId, fileId, token, offset, call.receiveChannel())) {
                    UploadResult.Ok -> call.respond(HttpStatusCode.OK, UploadStatus(0))
                    UploadResult.NotFound -> call.respond(HttpStatusCode.NotFound, ErrorResponse("unknown session"))
                    is UploadResult.OffsetMismatch -> call.respond(HttpStatusCode.Conflict, UploadStatus(r.current))
                    is UploadResult.Failed -> call.respond(HttpStatusCode.InternalServerError, ErrorResponse(r.message))
                }
            }

            get(Api.UPLOAD_STATUS) {
                val q = call.request.queryParameters
                val bytes = receive.status(q["sessionId"].orEmpty(), q["fileId"].orEmpty(), q["token"].orEmpty())
                if (bytes == null) call.respond(HttpStatusCode.NotFound, ErrorResponse("unknown session"))
                else call.respond(UploadStatus(bytes))
            }

            post(Api.CANCEL) {
                val sessionId = call.request.queryParameters["sessionId"].orEmpty()
                receive.cancel(sessionId, byRemote = true)
                call.respond(HttpStatusCode.OK, UploadStatus(0))
            }

            post(Api.BOX_LIST) {
                val req = call.receive<BoxListRequest>()
                val remote = remoteIp(call.request.origin.remoteAddress)
                discovery.upsert(Peer(req.info, remote, nowMillis()))
                when (val r = boxAccess.list(req, remote)) {
                    is ListResult.Ok -> call.respond(r.response)
                    ListResult.PinRequired -> call.respond(HttpStatusCode.Unauthorized, ErrorResponse("pin required"))
                    ListResult.Denied -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("denied"))
                    ListResult.Busy -> call.respond(HttpStatusCode.Conflict, ErrorResponse("busy"))
                }
            }

            get("${Api.BOX_ITEM}/{id}") {
                if (!boxAccess.authorize(call.request.queryParameters["token"])) {
                    call.respond(HttpStatusCode.Forbidden, ErrorResponse("not allowed")); return@get
                }
                val file = call.parameters["id"]?.let { boxAccess.openItem(it) }
                if (file == null) { call.respond(HttpStatusCode.NotFound, ErrorResponse("no such item")); return@get }
                val total = file.size
                val start = call.request.headers[HttpHeaders.Range]?.removePrefix("bytes=")?.substringBefore('-')?.toLongOrNull()?.coerceIn(0L, total) ?: 0L
                val status = if (start > 0) HttpStatusCode.PartialContent else HttpStatusCode.OK
                if (start > 0) call.response.header(HttpHeaders.ContentRange, "bytes $start-${total - 1}/$total")
                call.response.header(HttpHeaders.AcceptRanges, "bytes")
                call.respondBytesWriter(io.ktor.http.ContentType.Application.OctetStream, status, total - start) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        file.open().buffer().use { source ->
                            if (start > 0) source.skip(start)
                            val buf = ByteArray(com.dropboxx.core.AppInfo.IO_BUFFER_SIZE)
                            var remaining = total - start
                            while (remaining > 0) {
                                val n = source.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                                if (n == -1) break
                                writeFully(buf, 0, n)
                                remaining -= n
                            }
                            flush()
                        }
                    }
                }
            }
        }
    }

    /** Netty reports IPv4-mapped IPv6 for some stacks; normalise to plain IPv4. */
    private fun remoteIp(raw: String): String = raw.removePrefix("::ffff:").substringBefore('%')
}
