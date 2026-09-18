package com.dropnest.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DeviceType {
    @SerialName("android") ANDROID,
    @SerialName("windows") WINDOWS,
    @SerialName("macos") MACOS,
    @SerialName("linux") LINUX,
    @SerialName("ios") IOS,
    @SerialName("web") WEB,
    @SerialName("unknown") UNKNOWN;

    val isMobile: Boolean get() = this == ANDROID || this == IOS
}

/** Everything a peer needs to know to talk to us. Sent in discovery beacons and `/api/v1/info`. */
@Serializable
data class DeviceInfo(
    val id: String,
    val alias: String,
    val deviceType: DeviceType,
    /** SHA-256 of the DER-encoded TLS certificate, lowercase hex. Peers pin this. */
    val fingerprint: String,
    val port: Int,
    val protocolVersion: Int = 1,
    val appVersion: String = "",
)

/** A device we have seen on the network. */
/** How we reach a peer. Wi-Fi (incl. hotspot) is primary; Bluetooth is the slow fallback when there is no IP route. */
enum class Transport { WIFI, BLUETOOTH }

data class Peer(
    val info: DeviceInfo,
    /** IP address for WIFI, Bluetooth MAC for BLUETOOTH. */
    val address: String,
    val lastSeenMillis: Long,
    val trusted: Boolean = false,
    val transport: Transport = Transport.WIFI,
    /** Bluetooth MAC when the device was also seen over Bluetooth (fallback route). */
    val bluetoothAddress: String? = null,
) {
    val id: String get() = info.id
    val baseUrl: String get() = "https://$address:${info.port}"
    val viaBluetooth: Boolean get() = transport == Transport.BLUETOOTH
}
