package com.example.nexuswallet.feature.coin.bitcoin.data

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.coin.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinTransactionDto
import com.example.nexuswallet.feature.coin.FeeLevel
import com.example.nexuswallet.feature.coin.bitcoin.data.local.BitcoinTransactionEntity
import com.example.nexuswallet.feature.coin.bitcoin.data.remote.model.EsploraTransactionResponse
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toBitcoinNetwork
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
    network = network.name,
    isIncoming = isIncoming
)

fun EsploraTransactionResponse.toDomain(
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

    return BitcoinTransaction(
        id = "${walletId}_${txid}_${System.currentTimeMillis()}",
        walletId = walletId,
        coinType = CoinType.BITCOIN,
        fromAddress = fromAddress,
        toAddress = toAddress,
        amountSatoshis = amount,
        amountBtc = btcAmount.toPlainString(),
        feeSatoshis = fee,
        feeBtc = feeBtc,
        feePerByte = if (size > 0) fee.toDouble() / size else 0.0,
        estimatedSize = size.toLong() ?: 0,
        signedHex = "",
        txHash = txid,
        status = if (status.confirmed) TransactionStatus.SUCCESS else TransactionStatus.PENDING,
        note = null,
        timestamp = status.blockTime ?: (System.currentTimeMillis() / 1000),
        feeLevel = FeeLevel.NORMAL,
        network = network,
        isIncoming = isIncoming
    )
}