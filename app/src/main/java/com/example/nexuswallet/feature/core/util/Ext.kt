package com.example.nexuswallet.feature.core.util

import java.math.BigDecimal

private const val SATOSHIS_PER_BTC = 100_000_000L

fun BigDecimal.toSatoshis(): Long =
    multiply(BigDecimal(SATOSHIS_PER_BTC)).toLong()

fun String.decodeHex(): ByteArray {
    check(length % 2 == 0) { "Invalid hex string" }
    return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }