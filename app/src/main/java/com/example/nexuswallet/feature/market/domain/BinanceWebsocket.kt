package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.market.domain.model.ConnectionState
import com.example.nexuswallet.feature.market.domain.model.TokenPriceUpdate
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BinanceWebSocket {
    val fullUpdates: SharedFlow<Map<String, TokenPriceUpdate>>
    val connectionState: StateFlow<ConnectionState>
    fun connect()
    fun disconnect()
}