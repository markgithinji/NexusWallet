package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import kotlinx.serialization.Serializable

@Serializable
data class TokenBalance(
    val tokenId: String,
    val symbol: String,
    val name: String,
    val contractAddress: String,
    val balance: String,
    val balanceDecimal: String,
    val usdPrice: Double,
    val usdValue: Double,
    val logoUrl: String? = null,
    val decimals: Int = 18,
    val chain: ChainType
)

enum class ChainType {
    BITCOIN,
    ETHEREUM,
    ETHEREUM_SEPOLIA,
    SOLANA
}

enum class WalletType {
    BITCOIN, ETHEREUM, MULTICHAIN, SOLANA, ETHEREUM_SEPOLIA, USDC
}

@Serializable
data class WalletBalance(
    val walletId: String,
    val address: String,
    val nativeBalance: String,
    val nativeBalanceDecimal: String,
    val usdValue: Double,
    val tokens: List<TokenBalance> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis()
)

@Serializable
data class WalletBackup(
    val walletId: String,
    val encryptedMnemonic: String,
    val encryptedPrivateKey: String,
    val encryptionIV: String,
    val backupDate: Long,
    val walletType: WalletType,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class Transaction(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val valueDecimal: String,
    val gasPrice: String? = null,
    val gasUsed: String? = null,
    val timestamp: Long,
    val status: TransactionStatus,
    val chain: ChainType,
    val tokenTransfers: List<TokenTransfer> = emptyList()
)

@Serializable
data class TokenTransfer(
    val token: TokenBalance,
    val from: String,
    val to: String,
    val value: String,
    val valueDecimal: String
)

enum class TransactionStatus {
    PENDING, SUCCESS, FAILED
}