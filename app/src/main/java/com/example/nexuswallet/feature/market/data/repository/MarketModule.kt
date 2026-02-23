package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocket
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MarketModule {

    @Provides
    @Singleton
    fun provideBinanceWebSocket(): BinanceWebSocket {
        return BinanceWebSocket.getInstance()
    }

    @Provides
    @Singleton
    fun provideWebSocketRepository(
        binanceWebSocket: BinanceWebSocket
    ): WebSocketRepository {
        return WebSocketRepository(binanceWebSocket)
    }
}