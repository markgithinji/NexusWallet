package com.example.nexuswallet.feature.wallet.domain.model

data class AssetDisplayInfo(
    val id: String,
    val walletId: String,
    val name: String,
    val symbol: String,
    val network: Network,
    val networkDisplayName: String,
    val isTestnet: Boolean,
    val balance: String,
    val balanceFormatted: String,
    val usdValue: Double,
    val usdValueFormatted: String,
    val priceChangePercentage: Double?,
    val priceChangeFormatted: String?,
    val tokenCount: Int = 0,  // For tokens like SPL count under Solana
    val assetType: AssetType,
    val address: String,
    val externalId: String? = null  // For EVM tokens
)

enum class AssetType { // Temporary model used to help mappings in the ui. Should be replaced when we fully implement usdt and erc20
    BITCOIN,
    SOLANA,
    ETHEREUM,
    USDC,
    USDT,
    ERC20,
    SPL
}