package com.example.nexuswallet.feature.market.data.di

import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocketImpl
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CoinStatsApi
import com.example.nexuswallet.feature.market.data.remote.interceptor.CoinGeckoInterceptor
import com.example.nexuswallet.feature.market.data.remote.interceptor.CoinStatsInterceptor
import com.example.nexuswallet.feature.market.data.repository.CoinGeckoRepositoryImpl
import com.example.nexuswallet.feature.market.data.repository.MarketRepositoryImpl
import com.example.nexuswallet.feature.market.data.repository.WebSocketRepositoryImpl
import com.example.nexuswallet.feature.market.domain.BinanceWebSocket
import com.example.nexuswallet.feature.market.domain.repository.CoinGeckoRepository
import com.example.nexuswallet.feature.market.domain.repository.MarketRepository
import com.example.nexuswallet.feature.market.domain.repository.WebSocketRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MarketModule {

    @Binds
    @Singleton
    abstract fun bindCoinGeckoRepository(
        impl: CoinGeckoRepositoryImpl
    ): CoinGeckoRepository

    @Binds
    @Singleton
    abstract fun bindWebSocketRepository(
        impl: WebSocketRepositoryImpl
    ): WebSocketRepository

    @Binds
    @Singleton
    abstract fun bindMarketRepository(
        impl: MarketRepositoryImpl
    ): MarketRepository

    @Binds
    @Singleton
    abstract fun bindBinanceWebSocket(
        impl: BinanceWebSocketImpl
    ): BinanceWebSocket

    companion object {
        private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
        private const val COINSTATS_BASE_URL = "https://openapiv1.coinstats.app/"

        @Provides
        @Singleton
        @Named("coingecko_okhttp")
        fun provideCoinGeckoOkHttpClient(
            coinGeckoInterceptor: CoinGeckoInterceptor,
            okHttpClient: OkHttpClient
        ): OkHttpClient {
            return okHttpClient.newBuilder()
                .addInterceptor(coinGeckoInterceptor)
                .build()
        }

        @Provides
        @Singleton
        fun provideCoinGeckoApi(
            @Named("coingecko_okhttp") client: OkHttpClient,
            json: Json
        ): CoinGeckoApi {
            return Retrofit.Builder()
                .baseUrl(COINGECKO_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(CoinGeckoApi::class.java)
        }

        @Provides
        @Singleton
        @Named("coinstats_okhttp")
        fun provideCoinStatsOkHttpClient(
            coinStatsInterceptor: CoinStatsInterceptor,
            okHttpClient: OkHttpClient
        ): OkHttpClient {
            return okHttpClient.newBuilder()
                .addInterceptor(coinStatsInterceptor)
                .build()
        }

        @Provides
        @Singleton
        fun provideCoinStatsApi(
            @Named("coinstats_okhttp") client: OkHttpClient,
            json: Json
        ): CoinStatsApi {
            return Retrofit.Builder()
                .baseUrl(COINSTATS_BASE_URL)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(CoinStatsApi::class.java)
        }
    }
}
