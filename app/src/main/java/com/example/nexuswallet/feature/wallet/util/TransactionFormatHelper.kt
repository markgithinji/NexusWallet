package com.example.nexuswallet.feature.wallet.util

import com.example.nexuswallet.feature.core.util.formatCryptoAmount
import com.example.nexuswallet.feature.core.util.formatTimestamp

object TransactionFormatHelper {

    fun formatAmount(amount: String): String = amount.formatCryptoAmount()

    fun formatTimestamp(timestamp: Long): String = timestamp.formatTimestamp()
}
