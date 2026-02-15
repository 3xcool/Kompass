package com.tekmoon.kompass.util

import kotlin.random.Random

actual fun randomUUID(): String {
    return generateUUID()
}

fun generateUUID(): String {
    val randomBytes = ByteArray(16)
    Random.nextBytes(randomBytes)

    // Set the version to 4 (random UUID) and the variant to IETF
    randomBytes[6] = (randomBytes[6].toInt() and 0x0F or 0x40).toByte() // Version 4
    randomBytes[8] = (randomBytes[8].toInt() and 0x3F or 0x80).toByte() // Variant

    return randomBytes.joinToString("") { byte -> "%02x".format(byte) }
        .let {
            "${it.substring(0, 8)}-${it.substring(8, 12)}-${it.substring(12, 16)}-${it.substring(16, 20)}-${it.substring(20)}"
        }
}