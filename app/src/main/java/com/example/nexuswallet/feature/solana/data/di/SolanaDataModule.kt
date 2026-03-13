package com.example.nexuswallet.feature.solana.data.di

import android.content.Context
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionDao
import com.example.nexuswallet.feature.solana.data.remote.HeliusApi
import com.example.nexuswallet.feature.solana.data.remote.HeliusApiKeyInterceptor
import com.example.nexuswallet.feature.solana.data.repository.SolanaBlockchainRepositoryImpl
import com.example.nexuswallet.feature.solana.data.repository.SolanaTransactionRepositoryImpl
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.sol4k.Connection
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import com.example.nexuswallet.BuildConfig

@InstallIn(SingletonComponent::class)
object SolanaDataModule {

    @Provides
    @Singleton
    fun provideHeliusApiKey(): String {
        return BuildConfig.HELIUS_API_KEY
    }

    @Provides
    @Singleton
    fun provideHeliusApiKeyInterceptor(apiKey: String): HeliusApiKeyInterceptor {
        return HeliusApiKeyInterceptor(
            apiKey
        )
    }

    @Provides
    @Singleton
    @Named("heliusRpcDevnet")
    fun provideHeliusRpcDevnetConnection(): Connection {
        return Connection("https://devnet.helius-rpc.com/")
    }

    @Provides
    @Singleton
    @Named("heliusRpcMainnet")
    fun provideHeliusRpcMainnetConnection(): Connection {
        return Connection("https://mainnet.helius-rpc.com/")
    }

    @Provides
    @Singleton
    @Named("heliusApiDevnet")
    fun provideHeliusDevnetBaseUrl(): String {
        return "https://api-devnet.helius-rpc.com/v0/"
    }

    @Provides
    @Singleton
    @Named("heliusApiMainnet")
    fun provideHeliusMainnetBaseUrl(): String {
        return "https://api-mainnet.helius-rpc.com/v0/"
    }

    @Provides
    @Singleton
    fun provideHeliusOkHttpClient(
        apiKeyInterceptor: HeliusApiKeyInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideHeliusApi(
        @Named("heliusApiDevnet") devnetBaseUrl: String,
        @Named("heliusApiMainnet") mainnetBaseUrl: String,
        okHttpClient: OkHttpClient,
        json: Json
    ): HeliusApi {
        // Use appropriate URL based on build type or config
        val baseUrl = if (BuildConfig.DEBUG) devnetBaseUrl else mainnetBaseUrl

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HeliusApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSolanaBlockchainRepository(
        @Named("heliusRpcDevnet") rpcDevnetConnection: Connection,
        @Named("heliusRpcMainnet") rpcMainnetConnection: Connection,
        heliusApi: HeliusApi,
        logger: Logger
    ): SolanaBlockchainRepository {
        return SolanaBlockchainRepositoryImpl(
            rpcDevnetConnection = rpcDevnetConnection,
            rpcMainnetConnection = rpcMainnetConnection,
            heliusApi = heliusApi
        )
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionDao(database: WalletDatabase): SolanaTransactionDao {
        return database.solanaTransactionDao()
    }

    @Provides
    @Singleton
    fun provideSolanaTransactionRepository(
        solanaTransactionDao: SolanaTransactionDao,
        logger: Logger
    ): SolanaTransactionRepository {
        return SolanaTransactionRepositoryImpl(
            solanaTransactionDao,
            logger
        )
    }
}