package com.example.nexuswallet.feature.wallet.ui.coindetail

import com.example.nexuswallet.feature.wallet.domain.model.Coin
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo

data class CoinDetailState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val coin: Coin? = null,
    val walletId: String = "",
    val address: String = "",
    val balance: String = "0",
    val balanceFormatted: String = "",
    val usdValue: Double? = null,
    val transactions: List<TransactionDisplayInfo> = emptyList(),
    val ethGasBalance: String = "0",
    val evmTokens: List<EVMToken> = emptyList(),
    val splTokens: List<SPLToken> = emptyList(),
)