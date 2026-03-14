package com.example.nexuswallet.feature.wallet.domain.model

import kotlinx.serialization.Serializable


// ============ MAIN WALLET BALANCE ============

data class WalletBalance(
    val walletId: String,
    val lastUpdated: Long,
    val bitcoinBalances: Map<BitcoinNetwork, BitcoinBalance> = emptyMap(),
    val solanaBalances: Map<SolanaNetwork, SolanaBalance> = emptyMap(),
    val evmBalances: List<EVMBalance> = emptyList(),
    val splBalances: List<SPLBalance> = emptyList()
)
// ============ BITCOIN BALANCE ============

@Serializable
data class BitcoinBalance(
    val address: String,
    val satoshis: String,
    val btc: String,
    val usdValue: Double
)

// ============ SOLANA BALANCE ============

@Serializable
data class SolanaBalance(
    val address: String,
    val lamports: String,
    val sol: String,
    val usdValue: Double
)

// ============ EVM BALANCE ============

@Serializable
data class EVMBalance(
    val externalTokenId: String,
    val address: String,
    val balanceWei: String,
    val balanceDecimal: String,
    val usdValue: Double,
)
@Serializable
data class SPLBalance(
    val mintAddress: String,
    val address: String,
    val balanceDecimal: String,
    val usdValue: Double
)
