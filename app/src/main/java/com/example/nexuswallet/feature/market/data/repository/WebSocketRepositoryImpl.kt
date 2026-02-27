package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.market.data.remote.ConnectionState
import com.example.nexuswallet.feature.market.data.remote.TokenPriceUpdate
import com.example.nexuswallet.feature.market.domain.BinanceWebSocket
import com.example.nexuswallet.feature.market.domain.WebSocketRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebSocketRepositoryImpl @Inject constructor(
    private val webSocketManager: BinanceWebSocket
) : WebSocketRepository {

    init {
        webSocketManager.connect()
    }

    override fun getTokenUpdates(): Flow<Map<String, TokenPriceUpdate>> {
        return webSocketManager.fullUpdates
    }

    override fun getConnectionState(): Flow<Boolean> {
        return webSocketManager.connectionState.map { state ->
            state == ConnectionState.CONNECTED
        }
    }

    override fun reconnect() {
        webSocketManager.disconnect()
        webSocketManager.connect()
    }

    override fun disconnect() {
        webSocketManager.disconnect()
    }
}