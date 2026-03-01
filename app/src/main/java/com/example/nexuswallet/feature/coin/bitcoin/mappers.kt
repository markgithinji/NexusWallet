package com.example.nexuswallet.feature.coin.bitcoin

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toBitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toStorageString
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import java.math.BigDecimal
import java.math.RoundingMode
fun BitcoinTransactionEntity.toDomain(): BitcoinTransaction = BitcoinTransaction(
    id = id,
    walletId = walletId,
    fromAddress = fromAddress,
    toAddress = toAddress,
    status = TransactionStatus.valueOf(status),
    timestamp = timestamp,
    note = note,
    feeLevel = FeeLevel.valueOf(feeLevel),
    amountSatoshis = amountSatoshis,
    amountBtc = amountBtc,
    feeSatoshis = feeSatoshis,
    feeBtc = feeBtc,
    feePerByte = feePerByte,
    estimatedSize = estimatedSize,
    signedHex = signedHex,
    txHash = txHash,
    network = network.toBitcoinNetwork(),
    isIncoming = isIncoming
)

fun BitcoinTransaction.toEntity(): BitcoinTransactionEntity = BitcoinTransactionEntity(
    id = id,
    walletId = walletId,
    fromAddress = fromAddress,
    toAddress = toAddress,
    status = status.name,
    timestamp = timestamp,
    note = note,
    feeLevel = feeLevel.name,
    amountSatoshis = amountSatoshis,
    amountBtc = amountBtc,
    feeSatoshis = feeSatoshis,
    feeBtc = feeBtc,
    feePerByte = feePerByte,
    estimatedSize = estimatedSize,
    signedHex = signedHex,
    txHash = txHash,
    network = network.toStorageString(),
    isIncoming = isIncoming
)

fun BitcoinTransactionDto.toDomain(
    walletId: String,
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

    return BitcoinTransaction(
        id = "btc_tx_${System.currentTimeMillis()}_${txid.take(8)}",
        walletId = walletId,
        fromAddress = fromAddress,
        toAddress = toAddress,
        status = status,
        timestamp = timestamp * 1000,
        note = null,
        feeLevel = FeeLevel.NORMAL,
        amountSatoshis = amount,
        amountBtc = btcAmount.toPlainString(),
        feeSatoshis = fee,
        feeBtc = feeBtc,
        feePerByte = 0.0,
        estimatedSize = 0,
        signedHex = null,
        txHash = txid,
        network = network,
        isIncoming = isIncoming
    )
}