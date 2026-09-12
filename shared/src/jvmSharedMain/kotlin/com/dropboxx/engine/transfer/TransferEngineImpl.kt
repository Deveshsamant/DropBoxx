package com.dropboxx.engine.transfer

import com.dropboxx.domain.BrowseOutcome
import com.dropboxx.domain.SendOutcome
import com.dropboxx.domain.TransferEngine
import com.dropboxx.engine.box.BoxAccessController
import com.dropboxx.engine.box.BoxClient
import com.dropboxx.model.AccessDecision
import com.dropboxx.model.AccessRequest
import com.dropboxx.model.BoxEntry
import com.dropboxx.model.IncomingDecision
import com.dropboxx.model.IncomingRequest
import com.dropboxx.model.OutgoingItem
import com.dropboxx.model.Peer
import com.dropboxx.model.ReceivedContent
import com.dropboxx.model.TransferSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Facade the UI talks to; glues push (send/receive) and pull (box) over one session registry. */
class TransferEngineImpl(
    private val registry: SessionRegistry,
    private val sender: SendController,
    private val receiver: ReceiveController,
    private val boxClient: BoxClient,
    private val boxAccess: BoxAccessController,
) : TransferEngine {

    override val sessions: StateFlow<List<TransferSession>> get() = registry.sessions
    override val incomingRequest: StateFlow<IncomingRequest?> get() = receiver.incomingRequest
    override val accessRequest: StateFlow<AccessRequest?> get() = boxAccess.accessRequest
    override val receivedContent: Flow<ReceivedContent> get() = receiver.receivedContent

    override suspend fun send(peer: Peer, items: List<OutgoingItem>, pin: String?): SendOutcome = sender.send(peer, items, pin)
    override suspend fun browse(peer: Peer, pin: String?): BrowseOutcome = boxClient.browse(peer, pin)
    override fun download(peer: Peer, entries: List<BoxEntry>, accessToken: String): String = boxClient.download(peer, entries, accessToken)

    override fun respond(sessionId: String, decision: IncomingDecision) = receiver.respond(sessionId, decision)
    override fun respondAccess(requestId: String, decision: AccessDecision) = boxAccess.respond(requestId, decision)

    override fun cancel(sessionId: String) {
        when {
            sender.isOutgoing(sessionId) -> sender.cancel(sessionId)
            receiver.isActive(sessionId) -> receiver.cancel(sessionId, byRemote = false)
            boxClient.isDownloading(sessionId) -> boxClient.cancel(sessionId)
            else -> registry.remove(sessionId)
        }
    }

    override fun clearFinished() = registry.clearFinished()

    override fun remove(sessionId: String) {
        cancel(sessionId)
        registry.remove(sessionId)
    }
}
