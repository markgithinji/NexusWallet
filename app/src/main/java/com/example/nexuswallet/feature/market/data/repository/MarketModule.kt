package com.example.nexuswallet.feature.market.data.repository

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.market.data.remote.BinanceWebSocket
import com.example.nexuswallet.feature.market.data.remote.CoinGeckoApi
import com.example.nexuswallet.feature.market.data.remote.CryptoPanicApi
import com.example.nexuswallet.feature.wallet.data.repository.MarketRepository
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
        ioDispatcher: CoroutineDispatcher
    ): BinanceWebSocket {
        return BinanceWebSocket(
            okHttpClient,
            json,
            ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideCoinGeckoApi(
        okHttpClient: OkHttpClient,
        json: Json
    ): CoinGeckoApi {
        val clientWithApiKey = okHttpClient.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                val url = original.url.newBuilder()
                    .addQueryParameter("x_cg_demo_api_key", BuildConfig.COINGECKO_API_KEY)
                    .build()
                val request = original.newBuilder()
                    .url(url)
                    .build()
                chain.proceed(request)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(COINGECKO_BASE_URL)
            .client(clientWithApiKey)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CoinGeckoApi::class.java)
    }

    @Provides
    @Singleton
    fun provideCryptoPanicApi(
        okHttpClient: OkHttpClient,
        json: Json
    ): CryptoPanicApi {
        return Retrofit.Builder()
            .baseUrl(CRYPTOPANIC_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CryptoPanicApi::class.java)
    }

    @Provides
    @Singleton
    fun provideWebSocketRepository(
        binanceWebSocket: BinanceWebSocket
    ): WebSocketRepository {
        return WebSocketRepository(binanceWebSocket)
    }


    @Provides
    @Singleton
    fun provideMarketRepository(
        coinGeckoApi: CoinGeckoApi,
        cryptoPanicApi: CryptoPanicApi
    ): MarketRepository {
        return MarketRepository(
            coinGeckoApi,
            cryptoPanicApi)
    }
}