package com.example.nexuswallet.feature.bitcoin.ui.review

import com.example.nexuswallet.feature.core.domain.model.TransactionResult

sealed class BitcoinReviewEffect {
    data class TransactionResultEffect(val result: TransactionResult) : BitcoinReviewEffect()
    data class TransactionPrepared(val txId: String) : BitcoinReviewEffect()
}
