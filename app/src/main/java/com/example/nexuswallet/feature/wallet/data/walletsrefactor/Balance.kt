package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import com.example.nexuswallet.feature.wallet.ui.SPLBalance
import kotlinx.serialization.Serializable

@Serializable
enum class TokenType {
    NATIVE,     // Native ETH
    ERC20,      // Generic ERC20 token
    USDC,       // USD Coin (special handling for 1:1 USD peg)
    USDT        // Tether
}

// ============ MAIN WALLET BALANCE ============

@Serializable
data class WalletBalance(
    val walletId: String,
    val lastUpdated: Long,
    val bitcoinBalances: Map<String, BitcoinBalance> = emptyMap(),
    val solanaBalances: Map<String, SolanaBalance> = emptyMap(),
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
