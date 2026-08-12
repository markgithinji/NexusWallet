package com.example.nexuswallet.feature.wallet.ui.transactiondetail

import com.example.nexuswallet.feature.wallet.domain.model.TransactionDetail

data class TransactionDetailState(
    val transaction: TransactionDetail? = null,
    val formattedAmount: String = "",
    val formattedFee: String = "",
    val formattedTime: String = "",
    val usdValue: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isRefreshing: Boolean = false
)