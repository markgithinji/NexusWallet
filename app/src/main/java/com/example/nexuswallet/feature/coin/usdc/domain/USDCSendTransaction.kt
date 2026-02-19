package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionEntity
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class USDCSendTransaction(
    val id: String,
    val walletId: String,
    val coinType: CoinType = CoinType.USDC,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    // USDC specific fields
    val amount: String, // USDC units (6 decimals)
    val amountDecimal: String, // Human readable USDC
    val contractAddress: String,
    val network: EthereumNetwork,
    // Gas transaction fields
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    // Optional reference to ETH transaction
    val ethereumTransactionId: String? = null,
    val isIncoming: Boolean = false
)

@Serializable
sealed class EthereumNetwork {
    abstract val chainId: String
    abstract val usdcContractAddress: String
    abstract val isTestnet: Boolean
    abstract val displayName: String

    @Serializable
    @SerialName("Mainnet")
    data object Mainnet : EthereumNetwork() {
        override val chainId = "1"
        override val usdcContractAddress = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48"
        override val isTestnet = false
        override val displayName = "Mainnet"
    }

    @Serializable
    @SerialName("Sepolia")
    data object Sepolia : EthereumNetwork() {
        override val chainId = "11155111"
        override val usdcContractAddress = "0x1c7D4B196Cb0C7B01d743Fbc6116a902379C7238"
        override val isTestnet = true
        override val displayName = "Sepolia"
    }
}


data class USDCFeeEstimate(
    val gasPriceGwei: String,           // Gas price in Gwei
    val gasPriceWei: String,             // Gas price in Wei
    val gasLimit: Long,                   // Gas limit for token transfer
    val totalFeeWei: String,              // Total fee in Wei
    val totalFeeEth: String,               // Total fee in ETH (human readable)
    val estimatedTime: Int,                // Estimated time in seconds
    val priority: FeeLevel,
    val contractAddress: String,           // USDC contract address
    val tokenDecimals: Int = 6              // USDC always has 6 decimals
)

data class USDCWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: EthereumNetwork,
    val contractAddress: String
)

data class SendUSDCResult(
    val transactionId: String,
    val txHash: String,
    val success: Boolean,
    val error: String? = null
)
