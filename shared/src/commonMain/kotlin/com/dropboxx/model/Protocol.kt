package com.dropboxx.model

import kotlinx.serialization.Serializable

/*
 * DropBoxx protocol v1 — JSON over HTTPS between two devices on the same network.
 * See docs/PROTOCOL.md for the full description.
 */

@Serializable
data class MulticastMessage(
    val info: DeviceInfo,
    /** true = "I just came online, please register with me"; false = reply. */
    val announce: Boolean,
)

@Serializable
data class FileMeta(
    val id: String,
    val fileName: String,
    val size: Long,
    val fileType: String,
    val kind: ItemKind = ItemKind.FILE,
    /** Inline payload for TEXT / URL. */
    val content: String? = null,
)

@Serializable
data class PrepareUploadRequest(
    val info: DeviceInfo,
    val files: Map<String, FileMeta>,
    /** Receiver PIN, if the receiver requires one. */
    val pin: String? = null,
    /** Token granted by the receiver during an earlier "trust this device" acceptance. */
    val pairToken: String? = null,
)

@Serializable
data class PrepareUploadResponse(
    val sessionId: String,
    /** fileId -> per-file upload token. Only FILE items appear here. */
    val files: Map<String, String>,
    /** Present when the receiver chose to trust the sender: store it and send it next time. */
    val pairToken: String? = null,
)

@Serializable
data class UploadStatus(val bytesReceived: Long)

@Serializable
data class ErrorResponse(val message: String)

object Api {
    const val INFO = "/api/v1/info"
    const val REGISTER = "/api/v1/register"
    const val PREPARE_UPLOAD = "/api/v1/prepare-upload"
    const val UPLOAD = "/api/v1/upload"
    const val UPLOAD_STATUS = "/api/v1/upload-status"
    const val CANCEL = "/api/v1/cancel"
    const val BOX_LIST = "/api/v1/box/list"
    /** GET {BOX_ITEM}/{itemId}?token=... with optional Range header. */
    const val BOX_ITEM = "/api/v1/box/item"

    const val HEADER_OFFSET = "X-Offset"
}
