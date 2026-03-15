package com.example.nexuswallet.feature.wallet.ui.coindetail

import com.example.nexuswallet.feature.core.domain.model.CoinType
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.TransactionDisplayInfo
import java.math.BigDecimal

data class CoinDetailState(
    val walletId: String = "",
    val address: String = "",
    val balance: String = "0",
    val balanceFormatted: String = "0",
    val usdValue: Double = 0.0,
    val network: Network? = null,
    val networkDisplayName: String = "",
    val coinType: CoinType? = null,
    val ethGasBalance: BigDecimal? = null,
    val splTokens: List<SPLToken> = emptyList(),
    val evmTokens: List<EVMToken> = emptyList(),
    val transactions: List<TransactionDisplayInfo> = emptyList(),
    val externalTokenId: String? = null,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null
)