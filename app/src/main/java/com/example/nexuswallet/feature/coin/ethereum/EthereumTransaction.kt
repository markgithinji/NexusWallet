package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
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
    abstract val network: String
    abstract val isIncoming: Boolean
    abstract val tokenExternalId: String?
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
    override val network: String,
    override val isIncoming: Boolean = false,
    val data: String = "",
    override val tokenExternalId: String? = null
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
    override val network: String,
    override val isIncoming: Boolean = false,
    val tokenContract: String,
    val tokenSymbol: String,
    val tokenDecimals: Int,
    val data: String,
    override val tokenExternalId: String
) : EVMTransaction()

@Serializable
data class EVMFeeEstimate(
    val gasPriceGwei: String,           // Gas price in Gwei
    val gasPriceWei: String,             // Gas price in Wei
    val gasLimit: Long,                   // Gas limit (21000 for ETH, higher for tokens)
    val totalFeeWei: String,              // Total fee in Wei
    val totalFeeEth: String,               // Total fee in ETH (human readable)
    val estimatedTime: Int,                // Estimated time in seconds
    val priority: FeeLevel,
    val baseFee: String? = null,           // Base fee for EIP-1559 (optional)
    val isEIP1559: Boolean = false         // Whether this is an EIP-1559 fee estimate
)

@Serializable
data class EthereumWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: EthereumNetwork
)

@Serializable
data class SendEthereumResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)

data class CachedConfirmationTime(
    val seconds: Int,
    val timestamp: Long
)