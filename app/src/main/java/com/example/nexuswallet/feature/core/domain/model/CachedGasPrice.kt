package com.example.nexuswallet.feature.core.domain.model

data class CachedGasPrice(
    val price: GasPrice,
    val timestamp: Long
)