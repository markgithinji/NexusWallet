package com.example.nexuswallet.feature.market.ui

import com.example.nexuswallet.feature.market.domain.model.ConnectionState
import com.example.nexuswallet.feature.market.domain.model.Token

data class MarketUiState(
    val tokens: List<Token> = emptyList(),
    val filteredTokens: List<Token> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val connectionState: ConnectionState = ConnectionState.CONNECTING,
    val searchQuery: String = ""
)
