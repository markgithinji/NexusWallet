package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.wallet.domain.model.TransactionDetail
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.util.TransactionFormatHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FormatTransactionDetailDisplayUseCase @Inject constructor() {

    operator fun invoke(transaction: TransactionDetail): TransactionDisplayInfo {
        return TransactionDisplayInfo(
            id = transaction.id,
            isIncoming = transaction.isIncoming,
            amount = transaction.amount,
            formattedAmount = TransactionFormatHelper.formatAmount(transaction.amount),
            status = transaction.status,
            timestamp = transaction.timestamp,
            formattedTime = TransactionFormatHelper.formatTimestamp(transaction.timestamp),
            hash = transaction.hash,
            coinType = transaction.coinType
        )
    }
}