package com.example.nexuswallet.feature.bitcoin.util

object BitcoinConstants {
    // Transaction defaults
    const val DEFAULT_INPUT_COUNT = 1
    const val DEFAULT_OUTPUT_COUNT = 2

    // Transaction size constants (in bytes)
    const val BASE_TX_SIZE = 10L
    const val BYTES_PER_INPUT = 148L
    const val BYTES_PER_INPUT_SEGWIT = 68L
    const val BYTES_PER_INPUT_P2SH = 91L // Wrapped SegWit (P2SH-P2WPKH) approx
    const val BYTES_PER_OUTPUT = 34L
    const val BYTES_PER_OUTPUT_SEGWIT = 31L
    
    const val SATOSHIS_PER_BTC = 100_000_000L
    const val DUST_LIMIT = 546L
}