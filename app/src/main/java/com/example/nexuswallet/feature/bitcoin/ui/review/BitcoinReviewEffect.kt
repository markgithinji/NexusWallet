package com.example.nexuswallet.feature.bitcoin.ui.review

sealed class BitcoinReviewEffect {
    data class ShowError(val message: String) : BitcoinReviewEffect()
    data class TransactionPrepared(val txId: String) : BitcoinReviewEffect()
    data class TransactionSent(val txHash: String, val explorerUrl: String) : BitcoinReviewEffect()
}