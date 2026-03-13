package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.bitcoin.domain.model.BitcoinTransaction
import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.ethereum.domain.model.NativeETHTransaction
import com.example.nexuswallet.feature.ethereum.domain.model.TokenTransaction
import com.example.nexuswallet.feature.solana.domain.model.SolanaTransaction
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.util.TransactionFormatHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatTransactionDisplayUseCase @Inject constructor() {

    operator fun invoke(
        transaction: Any,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return when (transaction) {
            is BitcoinTransaction -> formatBitcoinTransaction(transaction, coinType)
            is NativeETHTransaction -> formatNativeETHTransaction(transaction, coinType)
            is TokenTransaction -> formatTokenTransaction(transaction, coinType)
            is SolanaTransaction -> formatSolanaTransaction(transaction, coinType)
            else -> throw IllegalArgumentException("Unknown transaction type: ${transaction::class.simpleName}")
        }
    }

    private fun formatBitcoinTransaction(
        tx: BitcoinTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountBtc,
            formattedAmount = TransactionFormatHelper.formatAmount(tx.amountBtc),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = TransactionFormatHelper.formatTimestamp(tx.timestamp),
            hash = tx.txHash,
            coinType = coinType
        )
    }

    private fun formatNativeETHTransaction(
        tx: NativeETHTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountEth,
            formattedAmount = TransactionFormatHelper.formatAmount(tx.amountEth),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = TransactionFormatHelper.formatTimestamp(tx.timestamp),
            hash = tx.txHash,
            coinType = coinType
        )
    }

    private fun formatTokenTransaction(
        tx: TokenTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountDecimal,
            formattedAmount = TransactionFormatHelper.formatAmount(tx.amountDecimal),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = TransactionFormatHelper.formatTimestamp(tx.timestamp),
            hash = tx.txHash,
            coinType = coinType
        )
    }

    private fun formatSolanaTransaction(
        tx: SolanaTransaction,
        coinType: CoinType
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = tx.id,
            isIncoming = tx.isIncoming,
            amount = tx.amountSol,
            formattedAmount = TransactionFormatHelper.formatAmount(tx.amountSol),
            status = tx.status,
            timestamp = tx.timestamp,
            formattedTime = TransactionFormatHelper.formatTimestamp(tx.timestamp),
            hash = tx.signature,
            coinType = coinType
        )
    }
}