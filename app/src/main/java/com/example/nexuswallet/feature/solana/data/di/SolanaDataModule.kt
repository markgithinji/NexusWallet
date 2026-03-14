package com.example.nexuswallet.feature.solana.data.di

import com.example.nexuswallet.BuildConfig
import com.example.nexuswallet.feature.core.data.remote.ApiKeyInterceptor
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionDao
import com.example.nexuswallet.feature.solana.data.remote.HeliusApi
import com.example.nexuswallet.feature.solana.data.repository.SolanaBlockchainRepositoryImpl
import com.example.nexuswallet.feature.solana.data.repository.SolanaTransactionRepositoryImpl
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import dagger.Provides
import dagger.hilt.InstallIn
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

@InstallIn(SingletonComponent::class)
object SolanaDataModule {

    @Provides
    @Singleton
    @Named("heliusApiKey")
    fun provideHeliusApiKey(): String {
        return BuildConfig.HELIUS_API_KEY
    }

    @Provides
    @Singleton
    @Named("heliusApiKeyParam")
    fun provideHeliusApiKeyParam(): String {
        return "api-key"
    }

    @Provides
    @Singleton
    @Named("helius")
    fun provideHeliusOkHttpClient(
        @Named("heliusApiKey") apiKey: String,
        @Named("heliusApiKeyParam") apiKeyParam: String
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
    @Named("heliusApiDevnet")
    fun provideHeliusDevnetApi(
        @Named("helius") okHttpClient: OkHttpClient,
        json: Json
    ): HeliusApi {
        return Retrofit.Builder()
            .baseUrl("https://api-devnet.helius-rpc.com/v0/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(HeliusApi::class.java)
    }

    @Provides
    @Singleton
    @Named("heliusApiMainnet")
    fun provideHeliusMainnetApi(
        @Named("helius") okHttpClient: OkHttpClient,
        json: Json
    ): HeliusApi {
        return Retrofit.Builder()
            .baseUrl("https://api-mainnet.helius-rpc.com/v0/")
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
        @Named("heliusApiDevnet") devnetApi: HeliusApi,
        @Named("heliusApiMainnet") mainnetApi: HeliusApi,
        logger: Logger
    ): SolanaBlockchainRepository {
        return SolanaBlockchainRepositoryImpl(
            rpcDevnetConnection = rpcDevnetConnection,
            rpcMainnetConnection = rpcMainnetConnection,
            devnetApi = devnetApi,
            mainnetApi = mainnetApi
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
            solanaTransactionDao = solanaTransactionDao,
            logger = logger
        )
    }
}