package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionEntity
import com.example.nexuswallet.feature.wallet.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
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
    val ethereumTransactionId: String? = null
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
        network = EthereumNetwork.valueOf(network),
        gasPriceWei = gasPriceWei,
        gasPriceGwei = gasPriceGwei,
        gasLimit = gasLimit,
        feeWei = feeWei,
        feeEth = feeEth,
        nonce = nonce,
        chainId = chainId,
        signedHex = signedHex,
        txHash = txHash,
        ethereumTransactionId = ethereumTransactionId
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
        network = network.name,
        gasPriceWei = gasPriceWei,
        gasPriceGwei = gasPriceGwei,
        gasLimit = gasLimit,
        feeWei = feeWei,
        feeEth = feeEth,
        nonce = nonce,
        chainId = chainId,
        signedHex = signedHex,
        txHash = txHash,
        ethereumTransactionId = ethereumTransactionId
    )
}