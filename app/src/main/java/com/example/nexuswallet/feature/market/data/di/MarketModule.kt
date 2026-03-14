package com.example.nexuswallet.feature.market.data.di

import com.example.nexuswallet.feature.core.data.remote.ApiKeyInterceptor
import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocketImpl
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.market.data.repository.CoinGeckoRepositoryImpl
import com.example.nexuswallet.feature.market.data.repository.MarketRepositoryImpl
import com.example.nexuswallet.feature.market.data.repository.WebSocketRepositoryImpl
import com.example.nexuswallet.feature.market.domain.BinanceWebSocket
import com.example.nexuswallet.feature.market.domain.CoinGeckoRepository
import com.example.nexuswallet.feature.market.domain.MarketRepository
import com.example.nexuswallet.feature.market.domain.WebSocketRepository
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
import com.example.nexuswallet.BuildConfig

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
        ioDispatcher: CoroutineDispatcher
    ): BinanceWebSocket {
        return BinanceWebSocketImpl(
            okHttpClient,
            json,
            ioDispatcher
        )
    }

    @Provides
    @Singleton
    @Named("coingecko")
    fun provideCoinGeckoApiKey(): String = BuildConfig.COINGECKO_API_KEY

    @Provides
    @Singleton
    @Named("coingecko")
    fun provideCoinGeckoApiKeyParam(): String = "x_cg_demo_api_key"

    @Provides
    @Singleton
    fun provideCoinGeckoApi(
        @Named("coingecko") apiKey: String,
        @Named("coingecko") apiKeyParam: String,
        json: Json,
        okHttpClient: OkHttpClient
    ): CoinGeckoApi {
        val clientWithInterceptor = okHttpClient.newBuilder()
            .addInterceptor(ApiKeyInterceptor(apiKey, apiKeyParam))
            .build()

        return Retrofit.Builder()
            .baseUrl(COINGECKO_BASE_URL)
            .client(clientWithInterceptor)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CoinGeckoApi::class.java)
    }

    @Provides
    @Singleton
    @Named("cryptopanic")
    fun provideCryptoPanicApiKey(): String = BuildConfig.CRYPTOPANIC_API_KEY

    @Provides
    @Singleton
    @Named("cryptopanic")
    fun provideCryptoPanicApiKeyParam(): String = "auth_token"

    @Provides
    @Singleton
    fun provideCryptoPanicApi(
        @Named("cryptopanic") apiKey: String,
        @Named("cryptopanic") apiKeyParam: String,
        json: Json,
        okHttpClient: OkHttpClient
    ): CryptoPanicApi {
        val clientWithInterceptor = okHttpClient.newBuilder()
            .addInterceptor(ApiKeyInterceptor(apiKey, apiKeyParam))
            .build()

        return Retrofit.Builder()
            .baseUrl(CRYPTOPANIC_BASE_URL)
            .client(clientWithInterceptor)
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
        cryptoPanicApi: CryptoPanicApi
    ): MarketRepository {
        return MarketRepositoryImpl(
            coinGeckoApi,
            cryptoPanicApi
        )
    }
}