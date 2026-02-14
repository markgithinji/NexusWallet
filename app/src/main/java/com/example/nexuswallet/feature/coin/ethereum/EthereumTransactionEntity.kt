package com.example.nexuswallet.feature.coin.ethereum

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.BigInteger

@Entity(tableName = "EthereumTransaction")
data class EthereumTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
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
    val data: String
)

fun EthereumTransactionEntity.toDomain(): EthereumTransaction {
    return EthereumTransaction(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = TransactionStatus.valueOf(status),
        timestamp = timestamp,
        note = note,
        feeLevel = FeeLevel.valueOf(feeLevel),
        amountWei = BigInteger(amountWei),
        amountEth = BigDecimal(amountEth),
        gasPriceWei = BigInteger(gasPriceWei),
        gasPriceGwei = BigDecimal(gasPriceGwei),
        gasLimit = gasLimit,
        feeWei = BigInteger(feeWei),
        feeEth = BigDecimal(feeEth),
        nonce = nonce,
        chainId = chainId,
        signedHex = signedHex,
        txHash = txHash,
        network = network,
        data = data
    )
}

fun EthereumTransaction.toEntity(): EthereumTransactionEntity {
    return EthereumTransactionEntity(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status.name,
        timestamp = timestamp,
        note = note,
        feeLevel = feeLevel.name,
        amountWei = amountWei.toString(),
        amountEth = amountEth.toPlainString(),
        gasPriceWei = gasPriceWei.toString(),
        gasPriceGwei = gasPriceGwei.toPlainString(),
        gasLimit = gasLimit,
        feeWei = feeWei.toString(),
        feeEth = feeEth.toPlainString(),
        nonce = nonce,
        chainId = chainId,
        signedHex = signedHex,
        txHash = txHash,
        network = network,
        data = data
    )
}