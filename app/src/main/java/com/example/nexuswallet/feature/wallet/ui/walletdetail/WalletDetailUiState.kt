package com.example.nexuswallet.feature.wallet.ui.walletdetail

import com.example.nexuswallet.feature.wallet.domain.model.AssetDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance

data class WalletDetailUiState(
    val wallet: Wallet? = null,
    val balance: WalletBalance? = null,
    val transactions: List<TransactionDisplayInfo> = emptyList(),
    val pricePercentages: Map<String, Double> = emptyMap(),

    // Unified assets list
    val assets: List<AssetDisplayInfo> = emptyList(),
    val totalBalanceFormatted: String = "$0.00",
    val selectedCurrency: String = "USD",

    // Granular loading states
    val isLoading: Boolean = false,
    val isLoadingBalance: Boolean = false,
    val isLoadingTransactions: Boolean = false,
    val isRefreshingBalance: Boolean = false,
    val isRefreshingTransactions: Boolean = false,

    // Timestamp for cache freshness
    val lastBalanceSyncTime: Long = 0,

    // Error states
    val error: String? = null,
    val hasSyncError: Boolean = false,
    val syncErrorMessage: String? = null,
    val balanceError: String? = null,
    val transactionsError: String? = null
)