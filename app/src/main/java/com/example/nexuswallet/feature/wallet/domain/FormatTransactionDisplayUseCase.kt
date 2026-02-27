package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.CoinType
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.TransactionDisplayInfo

interface FormatTransactionDisplayUseCase {
    operator fun invoke(
        transaction: Any,
        coinType: CoinType
    ): TransactionDisplayInfo

    fun formatTransactionList(
        transactions: List<Any>,
        coinType: CoinType
    ): List<TransactionDisplayInfo>
}