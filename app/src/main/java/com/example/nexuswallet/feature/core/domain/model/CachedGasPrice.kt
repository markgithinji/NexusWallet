package com.example.nexuswallet.feature.core.domain.model

import com.example.nexuswallet.feature.ethereum.data.model.GasPrice

data class CachedGasPrice(
    val price: GasPrice,
    val timestamp: Long
)