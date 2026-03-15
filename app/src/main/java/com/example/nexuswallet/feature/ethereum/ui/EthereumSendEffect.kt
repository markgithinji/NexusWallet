package com.example.nexuswallet.feature.ethereum.ui

sealed class EthereumSendEffect {
    data class ShowError(val message: String) : EthereumSendEffect()
    data class TransactionSent(val txHash: String, val explorerUrl: String) : EthereumSendEffect()
}