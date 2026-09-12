package com.dropboxx.domain

import com.dropboxx.model.DeviceType
import com.dropboxx.model.PlatformFile
import kotlinx.coroutines.flow.StateFlow
import okio.Sink

/** Where a received file is being written. */
interface ReceiveTarget {
    /** Human readable destination while in progress. */
    val displayPath: String

    /** Opens the destination for writing, appending after [bytesWritten] bytes. */
    fun sink(): Sink

    val bytesWritten: Long

    /** Finalises the file (rename / clear pending flag) and returns the final path or URI string. */
    suspend fun complete(): String

    suspend fun abort()
}

interface ReceiveStorage {
    suspend fun open(fileName: String, mimeType: String, expectedSize: Long): ReceiveTarget
    val saveDirectoryDisplay: String
}

/** Keeps box files reachable across restarts: desktop references paths, Android persists URI grants or copies. */
interface BoxFileStore {
    /** Returns a durable locator for [file], copying it into private storage when a reference would not survive. */
    suspend fun retain(file: PlatformFile, itemId: String): String
    /** Reopens a locator; null when the file is no longer accessible. */
    fun resolve(locator: String): PlatformFile?
    /** Frees anything [retain] copied. */
    fun release(locator: String)
}

interface FilePicker {
    suspend fun pickFiles(): List<PlatformFile>
    suspend fun pickDirectory(): String?
    val canPickDirectory: Boolean
}

data class HotspotState(
    val supported: Boolean,
    val active: Boolean = false,
    val starting: Boolean = false,
    val ssid: String? = null,
    val password: String? = null,
    val error: String? = null,
)

interface HotspotController {
    val state: StateFlow<HotspotState>
    suspend fun start()
    fun stop()
}

/** Per-OS glue the shared engine and UI need. */
interface PlatformServices {
    val deviceType: DeviceType
    val defaultAlias: String
    /** Private directory for keystore, trust list and history. */
    val dataDirectory: String
    val defaultSaveDirectory: String

    val receiveStorage: ReceiveStorage
    val boxFiles: BoxFileStore
    val filePicker: FilePicker
    val hotspot: HotspotController

    fun notify(title: String, body: String)
    fun openFile(pathOrUri: String)
    fun revealFile(pathOrUri: String)
    fun openUrl(url: String)
    /** Something Coil can load for a saved file (java.io.File, android Uri...), or null. */
    fun previewModel(pathOrUri: String): Any?

    /** Android: WifiManager multicast lock. Desktop: no-op. */
    fun setMulticastEnabled(enabled: Boolean)
    /** Keep the CPU/network awake during transfers. */
    fun setKeepAwake(enabled: Boolean)

    /** Set launch-at-login. Returns false if unsupported. */
    fun setLaunchAtStartup(enabled: Boolean): Boolean

    val supportsTray: Boolean
}
