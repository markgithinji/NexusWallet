package com.example.nexuswallet.feature.coin.ethereum.ui

sealed class EthereumSendEffect {
    data class ShowError(val message: String) : EthereumSendEffect()
    data class TransactionSent(val txHash: String) : EthereumSendEffect()
}