package com.dropnest.engine.bt

import com.dropnest.model.BoxListRequest
import com.dropnest.model.BoxListResponse
import com.dropnest.model.ChatAck
import com.dropnest.model.ChatEnvelope
import com.dropnest.model.DeviceInfo
import com.dropnest.engine.net.ProtocolJson
import kotlinx.serialization.Serializable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

/*
 * The HTTPS API, re-framed for a single RFCOMM stream:
 *   frame = kind (1 byte) + length (4 bytes, big endian) + payload
 *   kind 0 = JSON [BtMsg], 1 = file bytes, 2 = end of file bytes
 * One request/response per connection, except `get`, which streams the item after its reply.
 */
@Serializable
data class BtMsg(
    val type: String,
    val hello: DeviceInfo? = null,
    val chat: ChatEnvelope? = null,
    val chatAck: ChatAck? = null,
    val list: BoxListRequest? = null,
    val listRes: BoxListResponse? = null,
    val get: BtGet? = null,
    val getRes: BtGetRes? = null,
    /** For type "error": denied | pin | busy | notfound | <message>. */
    val error: String? = null,
) {
    companion object {
        const val HELLO = "hello"; const val CHAT = "chat"; const val CHAT_ACK = "chat-ack"
        const val LIST = "list"; const val LIST_RES = "list-res"; const val GET = "get"; const val GET_RES = "get-res"; const val ERROR = "error"
        fun error(code: String) = BtMsg(ERROR, error = code)
    }
}

@Serializable data class BtGet(val itemId: String, val token: String, val offset: Long = 0)
@Serializable data class BtGetRes(val size: Long, val start: Long)

object BtFrames {
    const val JSON: Int = 0
    const val DATA: Int = 1
    const val END: Int = 2
    const val MAX_FRAME = 4 * 1024 * 1024
    const val CHUNK = 32 * 1024

    class Reader(input: InputStream) {
        private val din = DataInputStream(input.buffered(64 * 1024))
        /** Returns kind to payload, or null at end of stream. */
        fun next(): Pair<Int, ByteArray>? {
            val kind = din.read()
            if (kind < 0) return null
            val len = din.readInt()
            if (len < 0 || len > MAX_FRAME) throw java.io.IOException("bad frame length $len")
            val buf = ByteArray(len)
            try { din.readFully(buf) } catch (e: EOFException) { return null }
            return kind to buf
        }
        fun message(): BtMsg? {
            val (kind, payload) = next() ?: return null
            if (kind != JSON) throw java.io.IOException("expected a message frame")
            return ProtocolJson.decodeFromString(BtMsg.serializer(), payload.decodeToString())
        }
    }

    class Writer(output: OutputStream) {
        private val dout = DataOutputStream(output.buffered(64 * 1024))
        @Synchronized fun frame(kind: Int, payload: ByteArray, len: Int = payload.size) {
            dout.write(kind); dout.writeInt(len); dout.write(payload, 0, len)
        }
        fun message(msg: BtMsg) { frame(JSON, ProtocolJson.encodeToString(BtMsg.serializer(), msg).encodeToByteArray()); flush() }
        fun data(buf: ByteArray, len: Int) = frame(DATA, buf, len)
        fun end() { frame(END, ByteArray(0)); flush() }
        @Synchronized fun flush() = dout.flush()
    }
}
