package com.dropnest.platform

import co.touchlab.kermit.Logger
import com.dropnest.engine.bt.BluetoothTransport
import com.dropnest.engine.bt.BtDevice
import com.dropnest.engine.bt.BtSocket
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Bluetooth on Windows through Winsock's RFCOMM address family (`AF_BTH`) via JNA - the JDK has
 * no Bluetooth API. Client only: the PC connects to phones that advertise the DropNest service.
 * Devices must be paired in Windows Settings first (Winsock refuses unpaired RFCOMM links).
 */
class WindowsBluetoothTransport : BluetoothTransport {

    private val log = Logger.withTag("Bluetooth")

    @Suppress("FunctionName")
    private interface Ws2 : Library {
        fun WSAStartup(version: Short, data: Pointer): Int
        fun socket(af: Int, type: Int, protocol: Int): Long
        fun connect(s: Long, name: Pointer, len: Int): Int
        fun send(s: Long, buf: Pointer, len: Int, flags: Int): Int
        fun recv(s: Long, buf: Pointer, len: Int, flags: Int): Int
        fun closesocket(s: Long): Int
        fun setsockopt(s: Long, level: Int, name: Int, value: Pointer, len: Int): Int
        fun WSAGetLastError(): Int
    }

    @Suppress("FunctionName")
    private interface Bth : Library {
        fun BluetoothFindFirstDevice(params: Pointer, info: Pointer): Pointer?
        fun BluetoothFindNextDevice(find: Pointer, info: Pointer): Boolean
        fun BluetoothFindDeviceClose(find: Pointer): Boolean
        fun BluetoothIsConnectable(radio: Pointer?): Boolean
    }

    private val ws2: Ws2? = runCatching { Native.load("ws2_32", Ws2::class.java) }.getOrNull()
    private val bth: Bth? = runCatching { Native.load("bthprops.cpl", Bth::class.java) }.getOrNull()

    override val supported: Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win") && ws2 != null && bth != null
    private val _ready = MutableStateFlow(false)
    override val ready: StateFlow<Boolean> get() = _ready

    init {
        if (ws2 != null) { val data = Memory(512); ws2.WSAStartup(0x0202.toShort(), data) }
        _ready.value = radioOn()
    }

    /** True while a Bluetooth radio is present and switched on. */
    private fun radioOn(): Boolean = supported && runCatching { bth!!.BluetoothIsConnectable(null) }.getOrDefault(false)

    override suspend fun enable(): Boolean { _ready.value = radioOn(); return _ready.value }
    override fun listen(onConnection: (BtSocket) -> Unit) = Unit   // Winsock server side needs SDP registration; phones connect to nothing here.
    override fun stopListening() = Unit

    // ---- paired devices (BLUETOOTH_DEVICE_INFO, 560 bytes) ----

    override suspend fun candidates(): List<BtDevice> = withContext(Dispatchers.IO) {
        val b = bth ?: return@withContext emptyList()
        _ready.value = radioOn()
        val out = ArrayList<BtDevice>()
        val params = Memory(40).apply {
            clear()
            setInt(0, 40)          // dwSize
            setInt(4, 1)           // fReturnAuthenticated
            setInt(8, 1)           // fReturnRemembered
            setInt(12, 0)          // fReturnUnknown
            setInt(16, 1)          // fReturnConnected
            setInt(20, 0)          // fIssueInquiry
            setByte(24, 0)         // cTimeoutMultiplier
            setPointer(32, null)   // hRadio
        }
        val info = Memory(560).apply { clear(); setInt(0, 560) }
        val find = runCatching { b.BluetoothFindFirstDevice(params, info) }.getOrNull() ?: return@withContext out
        try {
            do {
                val addr = info.getLong(8)
                val cod = info.getInt(16)
                val major = (cod shr 8) and 0x1F            // 1 = computer, 2 = phone
                val name = info.getWideString(64).trim()
                if (major == 1 || major == 2) out += BtDevice(mac(addr), name.ifEmpty { null })
                info.clear(); info.setInt(0, 560)
            } while (b.BluetoothFindNextDevice(find, info))
        } finally { runCatching { b.BluetoothFindDeviceClose(find) } }
        out
    }

    // ---- connect ----

    override suspend fun connect(address: String): BtSocket? = withContext(Dispatchers.IO) {
        val w = ws2 ?: return@withContext null
        val s = w.socket(AF_BTH, SOCK_STREAM, BTHPROTO_RFCOMM)
        if (s == INVALID_SOCKET) return@withContext null
        val addr = SockaddrBth(address, BluetoothTransport.SERVICE_UUID)
        val timeout = Memory(4).apply { setInt(0, 15_000) }
        w.setsockopt(s, SOL_SOCKET, SO_RCVTIMEO, timeout, 4)
        w.setsockopt(s, SOL_SOCKET, SO_SNDTIMEO, timeout, 4)
        if (w.connect(s, addr.pointer, addr.size()) != 0) {
            log.d { "connect $address failed: ${w.WSAGetLastError()}" }
            w.closesocket(s); return@withContext null
        }
        WinsockBtSocket(w, s, address)
    }

    /** SOCKADDR_BTH is 1-byte packed: family(2) + address(8) + service GUID(16) + port(4) = 30 bytes. */
    private class SockaddrBth(address: String, service: UUID) {
        val memory = Memory(30).apply {
            clear()
            setShort(0, AF_BTH.toShort())
            setLong(2, parseMac(address))
            write(10, guidBytes(service), 0, 16)
            setInt(26, 0)
        }
        val pointer: Pointer get() = memory
        fun size() = 30
    }

    private class WinsockBtSocket(private val w: Ws2, private val s: Long, override val remoteAddress: String) : BtSocket {
        @Volatile private var closed = false
        private val inBuf = Memory(64 * 1024); private val outBuf = Memory(64 * 1024)
        override val input: InputStream = object : InputStream() {
            override fun read(): Int { val b = ByteArray(1); val n = read(b, 0, 1); return if (n <= 0) -1 else b[0].toInt() and 0xFF }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (closed) return -1
                val want = minOf(len, inBuf.size().toInt())
                val n = w.recv(s, inBuf, want, 0)
                if (n == 0) return -1
                if (n < 0) throw IOException("recv failed: ${w.WSAGetLastError()}")
                inBuf.read(0, b, off, n); return n
            }
        }
        override val output: OutputStream = object : OutputStream() {
            override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)
            override fun write(b: ByteArray, off: Int, len: Int) {
                var sent = 0
                while (sent < len) {
                    val chunk = minOf(len - sent, outBuf.size().toInt())
                    outBuf.write(0, b, off + sent, chunk)
                    val n = w.send(s, outBuf, chunk, 0)
                    if (n <= 0) throw IOException("send failed: ${w.WSAGetLastError()}")
                    sent += n
                }
            }
        }
        override fun close() { if (!closed) { closed = true; runCatching { w.closesocket(s) } } }
    }

    private companion object {
        const val AF_BTH = 32
        const val SOCK_STREAM = 1
        const val BTHPROTO_RFCOMM = 3
        const val INVALID_SOCKET = -1L
        const val SOL_SOCKET = 0xFFFF
        const val SO_RCVTIMEO = 0x1006
        const val SO_SNDTIMEO = 0x1005

        fun parseMac(address: String): Long = address.split(':', '-').fold(0L) { acc, part -> (acc shl 8) or part.toLong(16) }
        fun mac(value: Long): String = (5 downTo 0).joinToString(":") { i -> "%02X".format((value shr (i * 8)) and 0xFF) }

        /** Windows GUID layout: Data1/2/3 little-endian, Data4 as-is. */
        fun guidBytes(uuid: UUID): ByteArray {
            val hi = uuid.mostSignificantBits; val lo = uuid.leastSignificantBits
            val d1 = (hi ushr 32).toInt(); val d2 = ((hi ushr 16) and 0xFFFF).toInt(); val d3 = (hi and 0xFFFF).toInt()
            val out = ByteArray(16)
            out[0] = d1.toByte(); out[1] = (d1 shr 8).toByte(); out[2] = (d1 shr 16).toByte(); out[3] = (d1 shr 24).toByte()
            out[4] = d2.toByte(); out[5] = (d2 shr 8).toByte()
            out[6] = d3.toByte(); out[7] = (d3 shr 8).toByte()
            for (i in 0 until 8) out[8 + i] = (lo ushr (56 - i * 8)).toByte()
            return out
        }
    }
}
