package com.dropboxx.core

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
