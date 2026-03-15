package com.example.nexuswallet.feature.core.util

import java.math.BigDecimal

private const val SATOSHIS_PER_BTC = 100_000_000L

fun BigDecimal.toSatoshis(): Long =
    multiply(BigDecimal(SATOSHIS_PER_BTC)).toLong()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }