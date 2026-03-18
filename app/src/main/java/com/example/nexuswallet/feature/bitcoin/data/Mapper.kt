package com.example.nexuswallet.feature.bitcoin.data

import com.example.nexuswallet.feature.bitcoin.data.local.BitcoinTransactionEntity
import com.example.nexuswallet.feature.bitcoin.data.remote.model.EsploraTransactionDto
import com.example.nexuswallet.feature.core.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode

fun BitcoinTransactionEntity.toDomain(): BitcoinTransaction =
    BitcoinTransaction(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = TransactionStatus.valueOf(status),
        timestamp = timestamp,
        note = note,
        feeLevel = FeeLevel.valueOf(feeLevel),
        network = network,
        isIncoming = isIncoming,
        txHash = txHash,
        amount = amountBtc,
        fee = feeBtc,
        symbol = "BTC",
        amountSatoshis = amountSatoshis,
        feeSatoshis = feeSatoshis,
        feePerByte = feePerByte,
        estimatedSize = estimatedSize,
        signedHex = signedHex ?: ""
    )

fun BitcoinTransaction.toEntity(): BitcoinTransactionEntity =
    BitcoinTransactionEntity(
        id = id,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status.name,
        timestamp = timestamp,
        note = note,
        feeLevel = feeLevel.name,
        amountSatoshis = amountSatoshis,
        amountBtc = amount,
        feeSatoshis = feeSatoshis,
        feeBtc = fee,
        feePerByte = feePerByte,
        estimatedSize = estimatedSize,
        signedHex = signedHex,
        txHash = txHash,
        network = network,
        isIncoming = isIncoming
    )

fun EsploraTransactionDto.toDomain(
    walletId: String,
    fromAddress: String,
    toAddress: String,
    amount: Long,
    isIncoming: Boolean,
    network: BitcoinNetwork
): BitcoinTransaction {

    val btcAmount = BigDecimal(amount).divide(
        BigDecimal(100_000_000),
        8,
        RoundingMode.HALF_UP
    )

    val feeBtc = if (fee > 0) {
        BigDecimal(fee).divide(
            BigDecimal(100_000_000),
            8,
            RoundingMode.HALF_UP
        ).toPlainString()
    } else "0"

    // Convert seconds to milliseconds for confirmed transactions
    val timestampMillis = when {
        status.blockTime != null -> {
            // blockTime is in seconds, convert to milliseconds
            status.blockTime * 1000L
        }
        status.confirmed -> {
            // If confirmed but no blockTime, use a reasonable default (24 hours ago)
            System.currentTimeMillis() - 24 * 60 * 60 * 1000
        }
        else -> {
            // Pending transaction - use current time
            System.currentTimeMillis()
        }
    }

    return BitcoinTransaction(
        id = txid,
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        network = network,
        isIncoming = isIncoming,
        txHash = txid,
        amount = btcAmount.toPlainString(),
        fee = feeBtc,
        symbol = "BTC",
        amountSatoshis = amount,
        feeSatoshis = fee,
        feePerByte = if (size > 0) fee.toDouble() / size else 0.0,
        estimatedSize = size.toLong(),
        signedHex = "",
        status = if (status.confirmed) TransactionStatus.SUCCESS else TransactionStatus.PENDING,
        note = null,
        timestamp = timestampMillis,
        feeLevel = FeeLevel.NORMAL
    )
}