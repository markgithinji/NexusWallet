package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.market.data.remote.ConnectionState
import com.example.nexuswallet.feature.market.data.remote.TokenPriceUpdate
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BinanceWebSocket {
    val fullUpdates: SharedFlow<Map<String, TokenPriceUpdate>>
    val connectionState: StateFlow<ConnectionState>
    fun connect()
    fun disconnect()
}