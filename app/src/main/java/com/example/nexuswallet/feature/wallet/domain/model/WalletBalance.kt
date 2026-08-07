package com.example.nexuswallet.feature.wallet.domain.model

import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.core.util.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

// ============ MAIN WALLET BALANCE ============

data class WalletBalance(
    val walletId: String,
    val lastUpdated: Long,
    val bitcoinBalances: Map<BitcoinNetwork, BitcoinBalance> = emptyMap(),
    val solanaBalances: Map<SolanaNetwork, SolanaBalance> = emptyMap(),
    val evmBalances: Map<String, EVMBalance> = emptyMap(),
    val splBalances: Map<String, SPLBalance> = emptyMap()
) {
    val totalUsdValue: BigDecimal
        get() {
            var total = BigDecimal.ZERO
            bitcoinBalances.values.forEach { total = total.add(it.usdValue) }
            solanaBalances.values.forEach { total = total.add(it.usdValue) }
            evmBalances.values.forEach { total = total.add(it.usdValue) }
            splBalances.values.forEach { total = total.add(it.usdValue) }
            return total
        }
}

// ============ BITCOIN BALANCE ============

@Serializable
data class BitcoinBalance(
    val address: String,
    val satoshis: String,
    val btc: String,
    @Serializable(with = BigDecimalSerializer::class)
    val usdValue: BigDecimal
)

// ============ SOLANA BALANCE ============

@Serializable
data class SolanaBalance(
    val address: String,
    val lamports: String,
    val sol: String,
    @Serializable(with = BigDecimalSerializer::class)
    val usdValue: BigDecimal
)

// ============ EVM BALANCE ============

@Serializable
data class EVMBalance(
    val evmTokenType: EVMTokenType,
    val network: EthereumNetwork,
    val address: String,
    val contractAddress: String,
    val balanceWei: String,
    val balanceDecimal: String,
    @Serializable(with = BigDecimalSerializer::class)
    val usdValue: BigDecimal
)

@Serializable
data class SPLBalance(
    val mintAddress: String,
    val address: String,
    val balanceDecimal: String,
    @Serializable(with = BigDecimalSerializer::class)
    val usdValue: BigDecimal
)
