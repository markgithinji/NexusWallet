package com.example.nexuswallet.feature.ethereum.data.di

import com.example.nexuswallet.feature.ethereum.data.local.EVMTransactionDao
import com.example.nexuswallet.feature.ethereum.data.remote.EtherscanApiService
import com.example.nexuswallet.feature.ethereum.data.remote.EtherscanInterceptor
import com.example.nexuswallet.feature.ethereum.data.repository.EVMBlockchainRepositoryImpl
import com.example.nexuswallet.feature.ethereum.data.repository.EVMTransactionRepositoryImpl
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import dagger.Binds
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class EVMDataModule {

    @Binds
    @Singleton
    abstract fun bindEVMBlockchainRepository(
        impl: EVMBlockchainRepositoryImpl
    ): EVMBlockchainRepository

    @Binds
    @Singleton
    abstract fun bindEVMTransactionRepository(
        impl: EVMTransactionRepositoryImpl
    ): EVMTransactionRepository

    companion object {
        private const val ETHERSCAN_V2_URL = "https://api.etherscan.io/"

        @Provides
        @Singleton
        @Named("etherscan_okhttp")
        fun provideEtherscanOkHttpClient(
            etherscanInterceptor: EtherscanInterceptor,
            okHttpClient: OkHttpClient
        ): OkHttpClient {
            return okHttpClient.newBuilder()
                .addInterceptor(etherscanInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        fun provideEtherscanApi(
            @Named("etherscan_okhttp") client: OkHttpClient,
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
    }
}
