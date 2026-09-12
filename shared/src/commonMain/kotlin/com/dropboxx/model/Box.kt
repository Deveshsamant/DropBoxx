package com.dropboxx.model

import kotlinx.serialization.Serializable

/**
 * Something the user dropped into their own box. Lives on this device until removed;
 * peers that are allowed in can list and download it.
 */
@Serializable
data class BoxItem(
    val id: String,
    val kind: ItemKind,
    val name: String,
    val size: Long,
    val mimeType: String,
    val addedAt: Long,
    /** FILE only: where the bytes live on this device (path / content URI). Never sent to peers. */
    val source: String? = null,
    /** TEXT / URL payload. */
    val content: String? = null,
    /** False when the source file disappeared (moved, deleted, permission lost). */
    val available: Boolean = true,
)

/** What a peer sees of a box item. */
@Serializable
data class BoxEntry(
    val id: String,
    val kind: ItemKind,
    val name: String,
    val size: Long,
    val mimeType: String,
    val addedAt: Long,
    val content: String? = null,
)

@Serializable
data class BoxListRequest(
    val info: DeviceInfo,
    val pairToken: String? = null,
    val pin: String? = null,
)

@Serializable
data class BoxListResponse(
    val owner: DeviceInfo,
    val items: List<BoxEntry>,
    /** Use as bearer token for downloads in this visit. */
    val accessToken: String,
    /** Present when the owner chose "always allow": persist it. */
    val pairToken: String? = null,
)

/** A peer wants to open our box and the user has to decide. */
data class AccessRequest(
    val id: String,
    val requester: DeviceInfo,
    val address: String,
    val receivedAt: Long,
)

data class AccessDecision(val allow: Boolean, val always: Boolean = false)

fun BoxItem.toEntry() = BoxEntry(id, kind, name, size, mimeType, addedAt, content)
