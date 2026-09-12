package com.dropboxx.core

import java.security.SecureRandom

private val secureRandom = SecureRandom()

actual fun secureToken(bytes: Int): String {
    val buf = ByteArray(bytes)
    secureRandom.nextBytes(buf)
    return buf.joinToString("") { "%02x".format(it) }
}

actual fun nowMillis(): Long = System.currentTimeMillis()
