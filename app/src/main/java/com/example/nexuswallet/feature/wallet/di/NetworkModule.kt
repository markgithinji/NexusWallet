package com.example.nexuswallet.feature.wallet.di

import com.example.nexuswallet.feature.wallet.data.remote.BitcoinBroadcastApiService
import com.example.nexuswallet.feature.wallet.data.remote.BlockstreamApiService
import com.example.nexuswallet.feature.wallet.data.remote.CovalentApiService
import com.example.nexuswallet.feature.wallet.data.remote.EtherscanApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val ETHERSCAN_BASE_URL = "https://api.etherscan.io/"
    private const val BLOCKSTREAM_BASE_URL = "https://blockstream.info/api/"
    private const val COVALENT_BASE_URL = "https://api.covalenthq.com/"

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideEtherscanApi(client: OkHttpClient): EtherscanApiService {
        return Retrofit.Builder()
            .baseUrl(ETHERSCAN_BASE_URL)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EtherscanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBlockstreamApi(client: OkHttpClient): BlockstreamApiService {
        return Retrofit.Builder()
            .baseUrl(BLOCKSTREAM_BASE_URL)
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

    private const val MEMPOOL_BASE_URL = "https://mempool.space/"

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