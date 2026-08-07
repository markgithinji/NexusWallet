package com.example.nexuswallet.feature.wallet.domain.model

import java.math.BigDecimal

data class AssetDisplayInfo(
    val id: String,
    val walletId: String,
    val coin: Coin,
    val name: String,
    val symbol: String,
    val network: Network,
    val isTestnet: Boolean,
    val balance: String,
    val balanceFormatted: String,
    val usdValue: BigDecimal,
    val usdValueFormatted: String,
    val priceChangePercentage: Double?,
    val priceChangeFormatted: String?,
    val address: String,
    val tokenCount: Int = 0,
    val externalId: String? = null
)