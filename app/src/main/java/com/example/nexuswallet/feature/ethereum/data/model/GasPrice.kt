package com.example.nexuswallet.feature.ethereum.data.model

data class GasPrice(
    val safe: String,
    val propose: String,
    val fast: String,
    val lastBlock: String? = null,
    val baseFee: String? = null,
    val safePriorityFee: String? = null,
    val proposePriorityFee: String? = null,
    val fastPriorityFee: String? = null
)