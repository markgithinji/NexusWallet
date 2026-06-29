package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.market.domain.model.ConnectionState
import com.example.nexuswallet.feature.market.domain.model.TokenPriceUpdate
import com.example.nexuswallet.feature.market.domain.BinanceWebSocket
import com.example.nexuswallet.feature.market.domain.repository.WebSocketRepository
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

    override fun getConnectionState(): Flow<ConnectionState> {
        return webSocketManager.connectionState
    }

    override fun reconnect() {
        webSocketManager.disconnect()
        webSocketManager.connect()
    }

    override fun disconnect() {
        webSocketManager.disconnect()
    }
}