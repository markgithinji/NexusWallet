package com.example.nexuswallet.feature.market.domain.repository

import com.example.nexuswallet.feature.market.domain.model.TokenPriceUpdate
import kotlinx.coroutines.flow.Flow

interface WebSocketRepository {
    fun getTokenUpdates(): Flow<Map<String, TokenPriceUpdate>>
    fun getConnectionState(): Flow<Boolean>
    fun reconnect()
    fun disconnect()
}