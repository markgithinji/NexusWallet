package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EthereumTransaction(
    val id: String,
    val walletId: String,
    val coinType: CoinType = CoinType.ETHEREUM,
    val fromAddress: String,
    val toAddress: String,
    val status: TransactionStatus,
    val timestamp: Long,
    val note: String?,
    val feeLevel: FeeLevel,
    val amountWei: String,
    val amountEth: String,
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val network: String,
    val data: String,
    val isIncoming: Boolean = false
)

data class EthereumFeeEstimate(
    val gasPriceGwei: String,           // Gas price in Gwei
    val gasPriceWei: String,             // Gas price in Wei
    val gasLimit: Long,                   // Gas limit (usually 21000 for simple transfers)
    val totalFeeWei: String,              // Total fee in Wei
    val totalFeeEth: String,               // Total fee in ETH (human readable)
    val estimatedTime: Int,                // Estimated time in seconds
    val priority: FeeLevel,
    val baseFee: String? = null,           // Base fee for EIP-1559 (optional)
    val isEIP1559: Boolean = false         // Whether this is an EIP-1559 fee estimate
)

data class EthereumWalletInfo(
    val walletId: String,
    val walletName: String,
    val walletAddress: String,
    val network: EthereumNetwork
)

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

