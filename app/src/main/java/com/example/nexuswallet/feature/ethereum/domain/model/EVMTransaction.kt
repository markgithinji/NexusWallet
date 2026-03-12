package com.example.nexuswallet.feature.ethereum.domain.model

import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.serialization.Serializable

@Serializable
sealed class EVMTransaction {
    abstract val id: String
    abstract val walletId: String
    abstract val fromAddress: String
    abstract val toAddress: String
    abstract val status: TransactionStatus
    abstract val timestamp: Long
    abstract val note: String?
    abstract val feeLevel: FeeLevel
    abstract val gasPriceWei: String
    abstract val gasPriceGwei: String
    abstract val gasLimit: Long
    abstract val feeWei: String
    abstract val feeEth: String
    abstract val nonce: Int
    abstract val chainId: Long
    abstract val signedHex: String?
    abstract val txHash: String?
    abstract val network: EthereumNetwork
    abstract val isIncoming: Boolean
    abstract val tokenExternalId: String?
    abstract val transactionType: EVMTransactionType
}

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
    override val txHash: String?,
    override val network: EthereumNetwork,
    override val isIncoming: Boolean = false,
    val data: String = "",
    override val tokenExternalId: String? = null,
    override val transactionType: EVMTransactionType = EVMTransactionType.NATIVE_ETH
) : EVMTransaction()

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
    val amountWei: String,
    val amountDecimal: String,
    override val gasPriceWei: String,
    override val gasPriceGwei: String,
    override val gasLimit: Long,
    override val feeWei: String,
    override val feeEth: String,
    override val nonce: Int,
    override val chainId: Long,
    override val signedHex: String?,
    override val txHash: String?,
    override val network: EthereumNetwork,
    override val isIncoming: Boolean = false,
    val tokenContract: String,
    val tokenSymbol: String,
    val tokenDecimals: Int,
    val data: String,
    override val tokenExternalId: String,
    override val transactionType: EVMTransactionType = EVMTransactionType.TOKEN
) : EVMTransaction()