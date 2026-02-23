package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocket
import com.example.nexuswallet.feature.market.data.remote.TokenPriceUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketRepository @Inject constructor(
    private val webSocketManager: BinanceWebSocket = BinanceWebSocket.getInstance()
) {
    init {
        webSocketManager.connect()
    }

    // For simple price updates
    fun getLivePrices(): Flow<Map<String, Double>> {
        return webSocketManager.priceUpdates
    }

    // Get full updates with price and percentage
    fun getFullTokenUpdates(): Flow<Map<String, TokenPriceUpdate>> {
        return webSocketManager.fullUpdates
    }

    fun getConnectionState(): Flow<Boolean> {
        return webSocketManager.connectionState.map { state ->
            state == BinanceWebSocket.ConnectionState.CONNECTED
        }
    }

    fun reconnect() {
        webSocketManager.disconnect()
        webSocketManager.connect()
    }

    fun disconnect() {
        webSocketManager.disconnect()
    }
}