package com.example.nexuswallet.feature.coin.bitcoin

import java.math.BigDecimal
import java.math.RoundingMode


private const val SATOSHIS_PER_BTC = 100_000_000L


fun BigDecimal.toSatoshis(): Long =
    multiply(BigDecimal(SATOSHIS_PER_BTC)).toLong()

fun Long.toBtc(): BigDecimal =
    BigDecimal(this).divide(BigDecimal(SATOSHIS_PER_BTC), 8, RoundingMode.HALF_UP)

fun Long.toBtcString(): String =
    if (this > 0) toBtc().toPlainString() else "0"

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }