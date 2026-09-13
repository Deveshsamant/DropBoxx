package com.dropnest.model

enum class Direction { SEND, RECEIVE }

enum class SessionStatus {
    /** Sender: waiting for the peer to accept. Receiver: waiting for the local user. */
    PENDING,
    ACTIVE,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED,
    DECLINED;

    val isTerminal: Boolean get() = this != PENDING && this != ACTIVE
}

enum class ItemStatus { QUEUED, ACTIVE, DONE, FAILED, SKIPPED }

data class TransferItem(
    val id: String,
    val name: String,
    val size: Long,
    val mimeType: String,
    val kind: ItemKind,
    val bytesDone: Long = 0,
    val status: ItemStatus = ItemStatus.QUEUED,
    /** Where the file ended up (receiver) — a path or content URI string. */
    val resultPath: String? = null,
    /** Inline payload for TEXT / URL items. */
    val content: String? = null,
    val error: String? = null,
)

data class TransferSession(
    val id: String,
    val direction: Direction,
    val peer: DeviceInfo,
    val peerAddress: String,
    val items: List<TransferItem>,
    val status: SessionStatus,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val bytesDone: Long = 0,
    val bytesTotal: Long = 0,
    val speedBps: Long = 0,
    val error: String? = null,
) {
    val progress: Float get() = if (bytesTotal <= 0) 0f else (bytesDone.toDouble() / bytesTotal).toFloat().coerceIn(0f, 1f)
    val fileCount: Int get() = items.count { it.kind == ItemKind.FILE }
}

/** A peer wants to send us something and the user has to decide. */
data class IncomingRequest(
    val sessionId: String,
    val sender: DeviceInfo,
    val senderAddress: String,
    val items: List<TransferItem>,
    val totalBytes: Long,
    val receivedAt: Long,
)

data class IncomingDecision(val accept: Boolean, val trustSender: Boolean = false)

/** Emitted whenever a TEXT / URL item arrives so the UI can copy / open it. */
data class ReceivedContent(val kind: ItemKind, val content: String, val from: DeviceInfo)
