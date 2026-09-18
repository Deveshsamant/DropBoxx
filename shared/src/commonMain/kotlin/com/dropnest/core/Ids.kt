package com.dropnest.core

import kotlin.random.Random

private const val ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"

/** URL-safe random identifier; 22 chars ~ 113 bits of entropy. */
fun randomId(length: Int = 22): String {
    val sb = StringBuilder(length)
    repeat(length) { sb.append(ALPHABET[Random.nextInt(ALPHABET.length)]) }
    return sb.toString()
}

/** Cryptographically strong token for pairing / session auth. */
expect fun secureToken(bytes: Int = 32): String

expect fun nowMillis(): Long

/** Local calendar fields of an epoch instant, for chat timestamps. */
data class LocalClock(val year: Int, val month: Int, val day: Int, val hour: Int, val minute: Int, val epochDay: Long)

expect fun localClock(millis: Long): LocalClock
