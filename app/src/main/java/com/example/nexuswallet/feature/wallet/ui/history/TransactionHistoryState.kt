package com.example.nexuswallet.feature.wallet.ui.history

import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo

data class TransactionHistoryState(
    val transactions: List<TransactionDisplayInfo> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)