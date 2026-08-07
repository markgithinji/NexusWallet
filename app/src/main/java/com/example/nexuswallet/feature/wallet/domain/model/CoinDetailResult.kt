package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.core.domain.model.Transaction
import java.math.BigDecimal

// Base result interface
sealed interface CoinDetailResult {
    val walletId: String
    val address: String
    val balance: String
    val balanceFormatted: String
    val usdValue: BigDecimal
    val network: Network
    val networkDisplayName: String
    val rawTransactions: List<Transaction>
}

data class BitcoinDetailResult(
    override val walletId: String,
    override val address: String,
    override val balance: String,
    override val balanceFormatted: String,
    override val usdValue: BigDecimal,
    override val network: BitcoinNetwork,
    override val networkDisplayName: String,
    override val rawTransactions: List<Transaction>,
    val bitcoinCoin: BitcoinCoin,
    val availableNetworks: List<BitcoinNetwork>
) : CoinDetailResult

data class EthereumDetailResult(
    override val walletId: String,
    override val address: String,
    override val balance: String,
    override val balanceFormatted: String,
    override val usdValue: BigDecimal,
    override val network: EthereumNetwork,
    override val networkDisplayName: String,
    override val rawTransactions: List<Transaction>,
    val token: EVMToken,
    val ethGasBalance: String,
    val availableTokens: List<EVMToken> = emptyList(),
    val chainId: String = network.chainId
) : CoinDetailResult

data class SolanaDetailResult(
    override val walletId: String,
    override val address: String,
    override val balance: String,
    override val balanceFormatted: String,
    override val usdValue: BigDecimal,
    override val network: SolanaNetwork,
    override val networkDisplayName: String,
    override val rawTransactions: List<Transaction>,
    val solanaCoin: SolanaCoin,
    val splTokens: List<SPLToken>,
    val availableNetworks: List<SolanaNetwork>
) : CoinDetailResult
