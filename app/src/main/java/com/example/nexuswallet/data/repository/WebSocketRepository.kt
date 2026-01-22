package com.example.nexuswallet.data.repository

import com.example.nexuswallet.data.remote.CoinCapWebSocket
import com.example.nexuswallet.data.remote.CryptoWebSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


class WebSocketRepository (
    private val webSocketManager: CryptoWebSocket = CryptoWebSocket.getInstance()
) {

    init {
        webSocketManager.connect()
    }

    fun getLivePrices(): Flow<Map<String, Double>> {
        return webSocketManager.priceUpdates.map { stringMap ->
            stringMap.mapValues { (_, value) ->
                value.toDoubleOrNull() ?: 0.0
            }
        }
    }

    fun getConnectionState(): Flow<Boolean> {
        return webSocketManager.connectionState.map { state ->
            state == CryptoWebSocket.ConnectionState.CONNECTED
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