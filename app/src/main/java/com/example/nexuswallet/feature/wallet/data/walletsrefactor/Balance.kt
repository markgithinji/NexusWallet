package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.BigInteger

@Serializable
data class WalletBalance(
    val walletId: String,
    val lastUpdated: Long,
    val bitcoin: BitcoinBalance? = null,
    val ethereum: EthereumBalance? = null,
    val solana: SolanaBalance? = null,
    val usdc: USDCBalance? = null
)

@Serializable
data class BitcoinBalance(
    val address: String,
    val satoshis: String,
    val btc: String,
    val usdValue: Double
)

@Serializable
data class EthereumBalance(
    val address: String,
    val wei: String,
    val eth: String,
    val usdValue: Double
)

@Serializable
data class SolanaBalance(
    val address: String,
    val lamports: String,
    val sol: String,
    val usdValue: Double
)

@Serializable
data class USDCBalance(
    val address: String,
    val amount: String, // USDC units (6 decimals)
    val amountDecimal: String, // Human readable USDC
    val usdValue: Double
)