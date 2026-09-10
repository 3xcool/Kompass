package com.tekmoon.kompass.util

import kotlin.random.Random

/**
 * Builds a version 4 UUID from random bytes.
 *
 * This is the same algorithm as the JVM actual, but it formats the bytes without
 * `String.format`, which the JVM standard library supplies and wasm does not.
 */
actual fun randomUUID(): String {
    val randomBytes = ByteArray(16)
    Random.nextBytes(randomBytes)

    // Set the version to 4 (random UUID) and the variant to IETF.
    randomBytes[6] = (randomBytes[6].toInt() and 0x0F or 0x40).toByte() // Version 4
    randomBytes[8] = (randomBytes[8].toInt() and 0x3F or 0x80).toByte() // Variant

    val hex = randomBytes.joinToString("") { byte ->
        byte.toUByte().toString(16).padStart(2, '0')
    }

    return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-" +
        "${hex.substring(16, 20)}-${hex.substring(20)}"
}
