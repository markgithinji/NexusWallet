package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EVMToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SPLToken
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoin
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaNetwork
import java.math.BigDecimal

// Base result interface
sealed interface CoinDetailResult {
    val walletId: String
    val address: String
    val balance: String
    val balanceFormatted: String
    val usdValue: Double
    val network: String
    val networkDisplayName: String
    val rawTransactions: List<Any>
}

// Bitcoin specific result
data class BitcoinDetailResult(
    override val walletId: String,
    override val address: String,
    override val balance: String,
    override val balanceFormatted: String,
    override val usdValue: Double,
    override val network: String,
    override val networkDisplayName: String,
    override val rawTransactions: List<Any>,
    val bitcoinCoin: BitcoinCoin,
    val availableNetworks: List<BitcoinNetwork>
) : CoinDetailResult

// Ethereum specific result
data class EthereumDetailResult(
    override val walletId: String,
    override val address: String,
    override val balance: String,
    override val balanceFormatted: String,
    override val usdValue: Double,
    override val network: String,
    override val networkDisplayName: String,
    override val rawTransactions: List<Any>,
    val token: EVMToken,
    val externalTokenId: String,
    val ethGasBalance: BigDecimal? = null,
    val availableTokens: List<EVMToken> = emptyList(),
    val chainId: String
) : CoinDetailResult

// Solana specific result
data class SolanaDetailResult(
    override val walletId: String,
    override val address: String,
    override val balance: String,
    override val balanceFormatted: String,
    override val usdValue: Double,
    override val network: String,
    override val networkDisplayName: String,
    override val rawTransactions: List<Any>,
    val solanaCoin: SolanaCoin,
    val splTokens: List<SPLToken>,
    val availableNetworks: List<SolanaNetwork>
) : CoinDetailResult