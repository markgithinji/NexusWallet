package com.example.nexuswallet.feature.coin.ethereum.data.di

import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.data.local.EVMTransactionDao
import com.example.nexuswallet.feature.coin.ethereum.data.remote.EtherscanApiService
import com.example.nexuswallet.feature.coin.ethereum.data.repository.EVMBlockchainRepositoryImpl
import com.example.nexuswallet.feature.coin.ethereum.data.repository.EVMTransactionRepositoryImpl
import com.example.nexuswallet.feature.coin.usdc.Web3jFactory
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
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
object EVMDataModule {

    private const val ETHERSCAN_V2_URL = "https://api.etherscan.io/"

    @Provides
    @Singleton
    fun provideDefaultEtherscanApi(
        client: OkHttpClient,
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
    fun provideEVMTransactionDao(database: WalletDatabase): EVMTransactionDao {
        return database.evmTransactionDao()
    }

    @Provides
    @Singleton
    fun provideEVMTransactionRepository(
        evmTransactionDao: EVMTransactionDao
    ): EVMTransactionRepository {
        return EVMTransactionRepositoryImpl(
            evmTransactionDao = evmTransactionDao)
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