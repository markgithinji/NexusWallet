package com.example.nexuswallet.feature.coin.solana.ui

sealed class SolanaSendEffect {
    data class ShowError(val message: String) : SolanaSendEffect()
    data class TransactionSent(val txHash: String) : SolanaSendEffect()
}