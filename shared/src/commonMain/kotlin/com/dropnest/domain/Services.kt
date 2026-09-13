package com.dropnest.domain

import com.dropnest.model.AccessDecision
import com.dropnest.model.AccessRequest
import com.dropnest.model.BoxEntry
import com.dropnest.model.BoxItem
import com.dropnest.model.DeviceInfo
import com.dropnest.model.IncomingDecision
import com.dropnest.model.IncomingRequest
import com.dropnest.model.OutgoingItem
import com.dropnest.model.PlatformFile
import com.dropnest.model.Peer
import com.dropnest.model.ReceivedContent
import com.dropnest.model.TransferSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Our own identity on the network. */
interface DeviceIdentity {
    val info: StateFlow<DeviceInfo>
}

interface DiscoveryService {
    val peers: StateFlow<List<Peer>>
    val scanning: StateFlow<Boolean>
    fun start()
    fun stop()
    /** Re-announce ourselves; cheap. */
    fun announce()
    /** Slow path: probe every host on the local /24 subnets. */
    suspend fun scanSubnet()
    fun forget(peerId: String)
    fun upsert(peer: Peer)
    /** Manual fallback: probe host[:port] directly. Returns the peer if it answered. */
    suspend fun addManual(address: String, port: Int): Peer?
}

sealed interface ServerState {
    data object Stopped : ServerState
    data object Starting : ServerState
    data class Running(val port: Int, val addresses: List<String>) : ServerState
    data class Failed(val message: String) : ServerState
}

interface LocalServer {
    val state: StateFlow<ServerState>
    fun start()
    fun stop()
    fun restart()
}

sealed interface SendOutcome {
    data object Started : SendOutcome
    data object PinRequired : SendOutcome
    data object Declined : SendOutcome
    data object Busy : SendOutcome
    data class Failed(val message: String) : SendOutcome
}

sealed interface BrowseOutcome {
    data class Ok(val items: List<BoxEntry>, val accessToken: String) : BrowseOutcome
    data object PinRequired : BrowseOutcome
    data object Denied : BrowseOutcome
    data object Busy : BrowseOutcome
    data class Failed(val message: String) : BrowseOutcome
}

/** The user's own box: persistent, survives restarts, browsable by allowed peers. */
interface BoxRepository {
    val items: StateFlow<List<BoxItem>>
    /** Imports items; returns the names that could not be read and were skipped. */
    suspend fun add(items: List<OutgoingItem>): List<String>
    fun remove(id: String)
    fun clear()
    /** Resolves a FILE item back to something streamable, or null when it is gone. */
    fun open(id: String): PlatformFile?
    fun toOutgoing(ids: Collection<String>): List<OutgoingItem>
}

interface TransferEngine {
    val sessions: StateFlow<List<TransferSession>>
    val incomingRequest: StateFlow<IncomingRequest?>
    val accessRequest: StateFlow<AccessRequest?>
    val receivedContent: Flow<ReceivedContent>

    /** Opens a peer's box. May block while the peer's user decides. */
    suspend fun browse(peer: Peer, pin: String? = null): BrowseOutcome
    /** Pulls entries from a peer's box into a RECEIVE session; returns the session id. */
    fun download(peer: Peer, entries: List<BoxEntry>, accessToken: String): String
    fun respondAccess(requestId: String, decision: AccessDecision)

    suspend fun send(peer: Peer, items: List<OutgoingItem>, pin: String? = null): SendOutcome
    fun respond(sessionId: String, decision: IncomingDecision)
    fun cancel(sessionId: String)
    fun clearFinished()
    fun remove(sessionId: String)
}

data class TrustedDevice(
    val id: String,
    val alias: String,
    val fingerprint: String,
    /** Secret the trusted peer must present to be auto-accepted. */
    val pairToken: String,
    val trustedAt: Long,
)

interface TrustStore {
    val devices: StateFlow<List<TrustedDevice>>
    fun find(id: String): TrustedDevice?
    fun trust(device: DeviceInfo, pairToken: String)
    fun revoke(id: String)
    /** Token we were given by a peer that trusts us. */
    fun outgoingToken(peerId: String): String?
    fun saveOutgoingToken(peerId: String, token: String)
}

interface HistoryStore {
    val entries: StateFlow<List<TransferSession>>
    fun add(session: TransferSession)
    fun remove(id: String)
    fun clear()
}
