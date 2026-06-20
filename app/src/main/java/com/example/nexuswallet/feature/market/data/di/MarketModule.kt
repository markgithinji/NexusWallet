package com.example.nexuswallet.feature.market.data.di

import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocketImpl
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.market.data.remote.interceptor.CoinGeckoInterceptor
import com.example.nexuswallet.feature.market.data.remote.interceptor.CryptoPanicInterceptor
import com.example.nexuswallet.feature.market.data.repository.CoinGeckoRepositoryImpl
import com.example.nexuswallet.feature.market.data.repository.MarketRepositoryImpl
import com.example.nexuswallet.feature.market.data.repository.WebSocketRepositoryImpl
import com.example.nexuswallet.feature.market.domain.BinanceWebSocket
import com.example.nexuswallet.feature.market.domain.CoinGeckoRepository
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.market.domain.WebSocketRepository
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
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
object MarketModule {

    private const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
    private const val CRYPTOPANIC_BASE_URL = "https://cryptopanic.com/api/developer/v2/"

    @Provides
    @Singleton
    fun provideBinanceWebSocket(
        okHttpClient: OkHttpClient,
        json: Json,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): BinanceWebSocket {
        return BinanceWebSocketImpl(
            okHttpClient,
            json,
            ioDispatcher
        )
    }

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
    @Named("cryptopanic_okhttp")
    fun provideCryptoPanicOkHttpClient(
        cryptoPanicInterceptor: CryptoPanicInterceptor,
        okHttpClient: OkHttpClient
    ): OkHttpClient {
        return okHttpClient.newBuilder()
            .addInterceptor(cryptoPanicInterceptor)
            .build()
    }

    @Provides
    @Singleton
    fun provideCryptoPanicApi(
        @Named("cryptopanic_okhttp") client: OkHttpClient,
        json: Json
    ): CryptoPanicApi {
        return Retrofit.Builder()
            .baseUrl(CRYPTOPANIC_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CryptoPanicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCoinGeckoRepository(
        coinGeckoApi: CoinGeckoApi
    ): CoinGeckoRepository {
        return CoinGeckoRepositoryImpl(coinGeckoApi)
    }

    @Provides
    @Singleton
    fun provideWebSocketRepository(
        binanceWebSocket: BinanceWebSocket
    ): WebSocketRepository {
        return WebSocketRepositoryImpl(binanceWebSocket)
    }

    @Provides
    @Singleton
    fun provideMarketRepository(
        coinGeckoApi: CoinGeckoApi,
        cryptoPanicApi: CryptoPanicApi,
        logger: Logger
    ): MarketRepository {
        return MarketRepositoryImpl(
            coinGeckoApi,
            cryptoPanicApi,
            logger
        )
    }
}