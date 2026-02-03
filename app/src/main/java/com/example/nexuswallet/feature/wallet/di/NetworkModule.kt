package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import android.util.Log
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.wallet.data.remote.BitcoinBroadcastApiService
import com.example.nexuswallet.feature.wallet.data.remote.BlockstreamApiService
import com.example.nexuswallet.feature.wallet.data.remote.CovalentApiService
import com.example.nexuswallet.feature.wallet.data.remote.EtherscanApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val ETHERSCAN_V2_URL = "https://api.etherscan.io/"

    private const val COVALENT_BASE_URL = "https://api.covalenthq.com/"
    private const val BLOCKSTREAM_MAINNET_URL = "https://blockstream.info/api/"
    private const val BLOCKSTREAM_TESTNET_URL = "https://blockstream.info/testnet/api/"
    private const val MEMPOOL_BASE_URL = "https://mempool.space/"

    @Provides
    @Singleton
    @Named("mainnetEtherscanApi")
    fun provideMainnetEtherscanApi(client: OkHttpClient): EtherscanApiService {
        return Retrofit.Builder()
            .baseUrl(ETHERSCAN_V2_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EtherscanApiService::class.java)
    }

    @Provides
    @Singleton
    @Named("sepoliaEtherscanApi")
    fun provideSepoliaEtherscanApi(client: OkHttpClient): EtherscanApiService {
        return Retrofit.Builder()
            .baseUrl(ETHERSCAN_V2_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EtherscanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideDefaultEtherscanApi(client: OkHttpClient): EtherscanApiService {
        return Retrofit.Builder()
            .baseUrl(ETHERSCAN_V2_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EtherscanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(ChainIdInterceptor())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // Interceptor to log chainid parameters
    class ChainIdInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val url = originalRequest.url

            // Log chainid if present
            url.queryParameter("chainid")?.let { chainid ->
                Log.d("NetworkModule", "Request chainid: $chainid")
            }

            return chain.proceed(originalRequest)
        }
    }

    @Provides
    @Singleton
    fun provideBlockstreamApi(
        client: OkHttpClient
    ): BlockstreamApiService {
        // For Bitcoin, we'll use testnet in debug builds
        val isTestnet = BuildConfig.DEBUG
        val baseUrl = if (isTestnet) BLOCKSTREAM_TESTNET_URL else BLOCKSTREAM_MAINNET_URL

        Log.d("NetworkModule", "Blockstream API using: $baseUrl")

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BlockstreamApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideCovalentApi(client: OkHttpClient): CovalentApiService {
        return Retrofit.Builder()
            .baseUrl(COVALENT_BASE_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CovalentApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBitcoinBroadcastApi(client: OkHttpClient): BitcoinBroadcastApiService {
        return Retrofit.Builder()
            .baseUrl(MEMPOOL_BASE_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BitcoinBroadcastApiService::class.java)
    }
}