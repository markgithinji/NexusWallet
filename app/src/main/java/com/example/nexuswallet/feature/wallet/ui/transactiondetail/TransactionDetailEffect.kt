package com.example.nexuswallet.feature.wallet.ui.transactiondetail

sealed class TransactionDetailEffect {
    data class ShowError(val message: String) : TransactionDetailEffect()
    data class CopyToClipboard(val text: String, val label: String) : TransactionDetailEffect()
    object ShareTransaction : TransactionDetailEffect()
    data class OpenExplorer(val url: String) : TransactionDetailEffect()
}