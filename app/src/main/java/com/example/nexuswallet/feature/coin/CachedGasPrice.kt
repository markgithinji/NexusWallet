package com.example.nexuswallet.feature.coin

import com.example.nexuswallet.feature.coin.ethereum.GasPrice

data class CachedGasPrice(
    val price: GasPrice,
    val timestamp: Long
)