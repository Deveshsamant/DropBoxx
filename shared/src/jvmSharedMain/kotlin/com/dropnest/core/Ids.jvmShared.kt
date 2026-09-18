package com.dropnest.core

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun secureToken(bytes: Int): String {
    val buf = ByteArray(bytes)
    secureRandom.nextBytes(buf)
    return buf.joinToString("") { "%02x".format(it) }
}

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun localClock(millis: Long): LocalClock {
    val t = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    return LocalClock(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute, t.toLocalDate().toEpochDay())
}
