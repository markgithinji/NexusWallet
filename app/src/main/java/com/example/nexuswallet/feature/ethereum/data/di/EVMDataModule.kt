package com.example.nexuswallet.feature.ethereum.data.di

import com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionDao
import com.example.nexuswallet.feature.ethereum.data.remote.EtherscanApiService
import com.example.nexuswallet.feature.ethereum.data.repository.EVMBlockchainRepositoryImpl
import com.example.nexuswallet.feature.ethereum.data.repository.EVMTransactionRepositoryImpl
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.usdc.Web3jFactory
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.core.data.remote.ApiKeyInterceptor
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
object EVMDataModule {

    private const val ETHERSCAN_V2_URL = "https://api.etherscan.io/"

    @Provides
    @Singleton
    @Named("etherscanApiKey")
    fun provideEtherscanApiKey(): String {
        return BuildConfig.ETHERSCAN_API_KEY
    }

    @Provides
    @Singleton
    @Named("etherscanApiKeyParam")
    fun provideEtherscanApiKeyParam(): String {
        return "apikey"
    }

    @Provides
    @Singleton
    @Named("etherscan")
    fun provideEtherscanOkHttpClient(
        @Named("etherscanApiKey") apiKey: String,
        @Named("etherscanApiKeyParam") apiKeyParam: String
    ): OkHttpClient {
        val interceptor = ApiKeyInterceptor(apiKey, apiKeyParam)

        return OkHttpClient.Builder()
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideEtherscanApi(
        @Named("etherscan") client: OkHttpClient,
        json: Json
    ): EtherscanApiService {
        return Retrofit.Builder()
            .baseUrl(ETHERSCAN_V2_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(EtherscanApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideEVMTransactionDao(database: WalletDatabase): EVMTransactionDao {
        return database.evmTransactionDao()
    }

    @Provides
    @Singleton
    fun provideEVMTransactionRepository(
        evmTransactionDao: EVMTransactionDao
    ): EVMTransactionRepository {
        return EVMTransactionRepositoryImpl(
            evmTransactionDao = evmTransactionDao
        )
    }

    @Provides
    @Singleton
    fun provideEVMBlockchainRepository(
        etherscanApiService: EtherscanApiService,
        web3jFactory: Web3jFactory
    ): EVMBlockchainRepository {
        return EVMBlockchainRepositoryImpl(
            etherscanApi = etherscanApiService,
            web3jFactory = web3jFactory
        )
    }
}