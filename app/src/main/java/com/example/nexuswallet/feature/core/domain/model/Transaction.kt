package com.example.nexuswallet.feature.core.domain.model

import com.example.nexuswallet.feature.ethereum.domain.model.EVMTransactionType
import com.example.nexuswallet.feature.ethereum.domain.model.TokenType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.serialization.Serializable

// Base sealed interface for all transactions
@Serializable
sealed interface Transaction {
    val id: String
    val walletId: String
    val fromAddress: String
    val toAddress: String
    val status: TransactionStatus
    val timestamp: Long
    val note: String?
    val feeLevel: FeeLevel
    val network: Network
    val isIncoming: Boolean
    val txHash: String?
    val amount: String
    val fee: String
    val symbol: String
}

@Serializable
data class BitcoinTransaction(
    override val id: String,
    override val walletId: String,
    override val fromAddress: String,
    override val toAddress: String,
    override val status: TransactionStatus,
    override val timestamp: Long,
    override val note: String?,
    override val feeLevel: FeeLevel,
    override val network: BitcoinNetwork,
    override val isIncoming: Boolean = false,
    override val txHash: String?,
    override val amount: String,
    override val fee: String,
    override val symbol: String = "BTC",
    val amountSatoshis: Long,
    val feeSatoshis: Long,
    val feePerByte: Double,
    val estimatedSize: Long,
    val signedHex: String?
) : Transaction

// Base EVM Transaction
@Serializable
sealed interface EVMTransaction : Transaction {
    override val network: EthereumNetwork
    val gasPriceWei: String
    val gasPriceGwei: String
    val gasLimit: Long
    val feeWei: String
    val feeEth: String
    val nonce: Int
    val chainId: Long
    val signedHex: String?
    val transactionType: EVMTransactionType
    val tokenType: TokenType?
    override val fee: String
}

// Native ETH Transaction
@Serializable
data class NativeETHTransaction(
    override val id: String,
    override val walletId: String,
    override val fromAddress: String,
    override val toAddress: String,
    override val status: TransactionStatus,
    override val timestamp: Long,
    override val note: String?,
    override val feeLevel: FeeLevel,
    override val network: EthereumNetwork,
    override val isIncoming: Boolean = false,
    override val txHash: String?,
    override val amount: String,
    override val fee: String,
    override val symbol: String,
    val amountWei: String,
    val amountEth: String,
    override val gasPriceWei: String,
    override val gasPriceGwei: String,
    override val gasLimit: Long,
    override val feeWei: String,
    override val feeEth: String,
    override val nonce: Int,
    override val chainId: Long,
    override val signedHex: String?,
    override val transactionType: EVMTransactionType = EVMTransactionType.NATIVE_ETH,
    override val tokenType: TokenType = TokenType.NATIVE,
    val data: String = ""
) : EVMTransaction

// Token Transaction
@Serializable
data class TokenTransaction(
    override val id: String,
    override val walletId: String,
    override val fromAddress: String,
    override val toAddress: String,
    override val status: TransactionStatus,
    override val timestamp: Long,
    override val note: String?,
    override val feeLevel: FeeLevel,
    override val network: EthereumNetwork,
    override val isIncoming: Boolean = false,
    override val txHash: String?,
    override val amount: String,
    override val fee: String,
    override val symbol: String,
    val amountWei: String,
    override val gasPriceWei: String,
    override val gasPriceGwei: String,
    override val gasLimit: Long,
    override val feeWei: String,
    override val feeEth: String,
    override val nonce: Int,
    override val chainId: Long,
    override val signedHex: String?,
    override val transactionType: EVMTransactionType = EVMTransactionType.TOKEN,
    override val tokenType: TokenType,
    val tokenContract: String,
    val data: String
) : EVMTransaction

@Serializable
data class SolanaTransaction(
    override val id: String,
    override val walletId: String,
    override val fromAddress: String,
    override val toAddress: String,
    override val status: TransactionStatus,
    override val timestamp: Long,
    override val note: String?,
    override val feeLevel: FeeLevel,
    override val network: SolanaNetwork,
    override val isIncoming: Boolean = false,
    override val txHash: String?,
    override val amount: String,
    override val fee: String,
    override val symbol: String,
    val amountLamports: Long,
    val feeLamports: Long,
    val signature: String?,
    val tokenMint: String? = null,
    val tokenSymbol: String? = null,
    val tokenName: String? = null,
    val tokenDecimals: Int? = null,
    val slot: Long? = null,
    val blockTime: Long? = null
) : Transaction