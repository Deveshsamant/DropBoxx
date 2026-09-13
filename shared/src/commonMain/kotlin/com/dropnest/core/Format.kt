package com.dropnest.core

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = -1
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val rounded = if (value >= 100) value.toLong().toString() else ((value * 10).toLong() / 10.0).toString()
    return "$rounded ${units[unit]}"
}

fun formatSpeed(bytesPerSecond: Long): String = "${formatBytes(bytesPerSecond)}/s"

fun formatEta(remainingBytes: Long, bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "--"
    val seconds = remainingBytes / bytesPerSecond
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}

fun shortFingerprint(fp: String): String =
    fp.take(16).chunked(4).joinToString(" ").uppercase()
