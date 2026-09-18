package com.dropnest.engine.bt

import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/** A nearby Bluetooth device as the OS reports it (paired or found in a scan). */
data class BtDevice(val address: String, val name: String?)

/** One RFCOMM connection. Streams are blocking; use them from IO threads. */
interface BtSocket : Closeable {
    val remoteAddress: String
    val input: InputStream
    val output: OutputStream
}

/**
 * Platform Bluetooth (classic RFCOMM). Android implements both sides; Windows only connects
 * (the JDK has no Bluetooth stack, so the desktop reaches out to phones that advertise the
 * DropNest service and never listens itself).
 */
interface BluetoothTransport {
    /** Hardware + permissions present; false hides the feature entirely. */
    val supported: Boolean
    /** Adapter on and permissions granted right now. */
    val ready: StateFlow<Boolean>
    /** Asks for runtime permissions / turns the feature on; returns true when usable. */
    suspend fun enable(): Boolean
    /** Accept incoming connections; no-op where unsupported. */
    fun listen(onConnection: (BtSocket) -> Unit)
    fun stopListening()
    /** Paired devices plus whatever a short scan finds. */
    suspend fun candidates(): List<BtDevice>
    /** Opens an RFCOMM channel to the DropNest service on [address], or null. */
    suspend fun connect(address: String): BtSocket?

    companion object {
        /** SDP service id both sides use. */
        val SERVICE_UUID: UUID = UUID.fromString("7f1d3a2e-5c6b-4e8f-9a0b-d2e4c6a8f0b1")
        const val SERVICE_NAME = "DropNest"
    }
}
