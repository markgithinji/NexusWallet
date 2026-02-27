package com.example.nexuswallet.feature.market.domain

import com.example.nexuswallet.feature.market.data.remote.TokenPriceUpdate
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    fun getTokenUpdates(): Flow<Map<String, TokenPriceUpdate>>
    fun getConnectionState(): Flow<Boolean>
    fun reconnect()
    fun disconnect()
}