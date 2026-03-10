package com.example.nexuswallet.feature.coin.bitcoin.ui.review

sealed class BitcoinReviewEffect {
    data class ShowError(val message: String) : BitcoinReviewEffect()
    data class TransactionPrepared(val transactionId: String) : BitcoinReviewEffect()
    data class TransactionSent(val txHash: String) : BitcoinReviewEffect()
}