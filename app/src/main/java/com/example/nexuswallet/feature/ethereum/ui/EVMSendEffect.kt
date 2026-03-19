package com.example.nexuswallet.feature.ethereum.ui

sealed class EVMSendEffect {
    data class ShowError(val message: String) : EVMSendEffect()
    data class TransactionSent(val txHash: String, val explorerUrl: String) : EVMSendEffect()
}