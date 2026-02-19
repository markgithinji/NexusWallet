package com.example.nexuswallet.feature.coin.usdc

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.USDCSendTransaction
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus


@Entity(tableName = "USDCSendTransaction")
data class USDCTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
    val amount: String,
    val amountDecimal: String,
    val contractAddress: String,
    val network: String,
    val gasPriceWei: String,
    val gasPriceGwei: String,
    val gasLimit: Long,
    val feeWei: String,
    val feeEth: String,
    val nonce: Int,
    val chainId: Long,
    val signedHex: String?,
    val txHash: String?,
    val ethereumTransactionId: String? = null,
    val isIncoming: Boolean = false
)

fun USDCTransactionEntity.toDomain(): USDCSendTransaction {
    return USDCSendTransaction(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = TransactionStatus.valueOf(status),
        timestamp = timestamp,
        note = note,
        feeLevel = FeeLevel.valueOf(feeLevel),
        amount = amount,
        amountDecimal = amountDecimal,
        contractAddress = contractAddress,
        network = network.toEthereumNetwork(),
        gasPriceWei = gasPriceWei,
        gasPriceGwei = gasPriceGwei,
        gasLimit = gasLimit,
        feeWei = feeWei,
        feeEth = feeEth,
        nonce = nonce,
        chainId = chainId,
        signedHex = signedHex,
        txHash = txHash,
        ethereumTransactionId = ethereumTransactionId,
        isIncoming = isIncoming
    )
}

fun USDCSendTransaction.toEntity(): USDCTransactionEntity {
    return USDCTransactionEntity(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status.name,
        timestamp = timestamp,
        note = note,
        feeLevel = feeLevel.name,
        amount = amount,
        amountDecimal = amountDecimal,
        contractAddress = contractAddress,
        network = network.toNetworkString(),
        gasPriceWei = gasPriceWei,
        gasPriceGwei = gasPriceGwei,
        gasLimit = gasLimit,
        feeWei = feeWei,
        feeEth = feeEth,
        nonce = nonce,
        chainId = chainId,
        signedHex = signedHex,
        txHash = txHash,
        ethereumTransactionId = ethereumTransactionId,
        isIncoming = isIncoming
    )
}
// Extension functions for conversion
fun String.toEthereumNetwork(): EthereumNetwork {
    return when (this.lowercase()) {
        "mainnet" -> EthereumNetwork.Mainnet
        "sepolia" -> EthereumNetwork.Sepolia
        else -> throw IllegalArgumentException("Unknown Ethereum network: $this")
    }
}

fun EthereumNetwork.toNetworkString(): String {
    return when (this) {
        is EthereumNetwork.Mainnet -> "mainnet"
        is EthereumNetwork.Sepolia -> "sepolia"
    }
}
