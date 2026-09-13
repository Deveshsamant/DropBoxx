package com.dropnest.core

/** Compile-time constants shared by every platform. */
object AppInfo {
    const val NAME = "DropNest"
    const val VERSION = "1.0.0"
    const val PROTOCOL_VERSION = 1

    /** HTTPS API port. If busy the server falls back to a random free port; discovery carries the real one. */
    const val DEFAULT_PORT = 47843

    /** UDP multicast used for zero-config discovery on the LAN / hotspot. */
    const val MULTICAST_GROUP = "239.255.77.77"
    const val MULTICAST_PORT = 47842

    /** Peers that have not been heard from for this long disappear from the list. */
    const val PEER_TTL_MILLIS = 45_000L
    const val ANNOUNCE_INTERVAL_MILLIS = 12_000L

    /** How long an incoming request waits for the user before it is auto-declined. */
    const val ACCEPT_TIMEOUT_MILLIS = 90_000L

    /** Inline text / link payloads above this size are rejected (they are meant to be small). */
    const val MAX_INLINE_TEXT_BYTES = 256 * 1024

    /** Streaming buffer for disk <-> socket copies. */
    const val IO_BUFFER_SIZE = 512 * 1024

    /** Concurrent file uploads per session. */
    const val PARALLEL_UPLOADS = 3

    /** UI progress snapshots are published at most this often. */
    const val PROGRESS_TICK_MILLIS = 200L
}
