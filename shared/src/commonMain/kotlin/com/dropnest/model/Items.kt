package com.dropnest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.Source

@Serializable
enum class ItemKind {
    @SerialName("file") FILE,
    @SerialName("text") TEXT,
    @SerialName("url") URL,
}

/** Platform-agnostic handle to something the user wants to send. */
interface PlatformFile {
    val name: String
    val size: Long
    val mimeType: String

    /** Something Coil can render (java.io.File, android Uri, ...) or null when no preview is possible. */
    val previewModel: Any? get() = null

    /** Opens a fresh read stream. May be called more than once (resume). */
    fun open(): Source
}

/** An item staged in the send area. */
sealed interface OutgoingItem {
    val id: String
    val kind: ItemKind
    val displayName: String
    val size: Long

    data class File(override val id: String, val file: PlatformFile) : OutgoingItem {
        override val kind get() = ItemKind.FILE
        override val displayName get() = file.name
        override val size get() = file.size
    }

    data class Text(override val id: String, val text: String) : OutgoingItem {
        override val kind get() = ItemKind.TEXT
        override val displayName get() = text.lineSequence().first().take(60)
        override val size get() = text.encodeToByteArray().size.toLong()
    }

    data class Url(override val id: String, val url: String) : OutgoingItem {
        override val kind get() = ItemKind.URL
        override val displayName get() = url
        override val size get() = url.encodeToByteArray().size.toLong()
    }
}

fun String.looksLikeUrl(): Boolean {
    val t = trim()
    if (t.contains('\n') || t.contains(' ')) return false
    return t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true)
}
