package com.dropnest.model

import kotlinx.serialization.Serializable

/** PENDING = waiting for the peer to be nearby; SENT = the peer's device acknowledged it; FAILED = it refused (not trusted / PIN). */
@Serializable
enum class MessageStatus { PENDING, SENT, FAILED }

@Serializable
data class ChatMessage(
    val id: String,
    val peerId: String,
    val fromMe: Boolean,
    val text: String,
    /** Wall clock on the sender when it was written. */
    val sentAt: Long,
    val status: MessageStatus = MessageStatus.SENT,
    val read: Boolean = true,
    /** Why delivery failed, shown under the bubble. */
    val error: String? = null,
    /** A file dropped privately for this peer; lives in the sender's box until removed. */
    val attachment: ChatAttachment? = null,
    /** Receiver side: where the fetched file landed. */
    val localPath: String? = null,
)

@Serializable
data class ChatAttachment(val itemId: String, val name: String, val size: Long, val mimeType: String, val kind: ItemKind = ItemKind.FILE)

/** One message on the wire. */
@Serializable
data class ChatWire(val id: String, val text: String, val sentAt: Long, val attachment: ChatAttachment? = null)

/** `POST /api/v1/chat`: everything the sender has queued for this device. */
@Serializable
data class ChatEnvelope(
    val from: DeviceInfo,
    val pairToken: String? = null,
    val pin: String? = null,
    val messages: List<ChatWire>,
)

@Serializable
data class ChatAck(
    /** Ids the receiver stored (already-known ids are acknowledged too). */
    val accepted: List<String>,
    /** Present when the receiver chose "always allow": persist it. */
    val pairToken: String? = null,
)

/** One thread as the Chats screen lists it. */
data class Conversation(
    val peer: DeviceInfo,
    val lastMessage: ChatMessage?,
    val unread: Int,
    val online: Boolean,
    val pending: Int,
)
