package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.core.domain.model.Transaction
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.util.TransactionFormatHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatTransactionDisplayUseCase @Inject constructor() {

    operator fun invoke(
        transaction: Transaction,
        coin: Coin
    ): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = transaction.id,
            isIncoming = transaction.isIncoming,
            amount = transaction.amount,
            formattedAmount = TransactionFormatHelper.formatAmount(transaction.amount),
            status = transaction.status,
            timestamp = transaction.timestamp,
            formattedTime = TransactionFormatHelper.formatTimestamp(transaction.timestamp),
            hash = transaction.txHash,
            coin = coin,
            symbol = coin.symbol
        )
    }
}