package com.example.nexuswallet.feature.wallet.di

import android.content.Context
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val ETHERSCAN_MAINNET_URL = "https://api.etherscan.io/"
    private const val ETHERSCAN_GOERLI_URL = "https://api-goerli.etherscan.io/"
    private const val ETHERSCAN_SEPOLIA_URL = "https://api-sepolia.etherscan.io/"
    private const val COVALENT_BASE_URL = "https://api.covalenthq.com/"


    private const val BLOCKSTREAM_MAINNET_URL = "https://blockstream.info/api/"
    private const val BLOCKSTREAM_TESTNET_URL = "https://blockstream.info/testnet/api/"

    @Provides
    @Singleton
    @Named("mainnetEtherscanApi")
    fun provideMainnetEtherscanApi(client: OkHttpClient): EtherscanApiService {
        return Retrofit.Builder()
            .baseUrl(ETHERSCAN_MAINNET_URL)
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
            .baseUrl(ETHERSCAN_SEPOLIA_URL)  // Sepolia-specific
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideEtherscanApi(
        client: OkHttpClient,
        @ApplicationContext context: Context
    ): EtherscanApiService {
        val isTestnet = BuildConfig.DEBUG // Use testnet in debug builds
        val baseUrl = when {
            isTestnet -> ETHERSCAN_SEPOLIA_URL
            else -> ETHERSCAN_MAINNET_URL
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EtherscanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideBlockstreamApi(
        client: OkHttpClient,
        @ApplicationContext context: Context
    ): BlockstreamApiService {
        val isTestnet = BuildConfig.DEBUG
        val baseUrl = if (isTestnet) BLOCKSTREAM_TESTNET_URL else BLOCKSTREAM_MAINNET_URL

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