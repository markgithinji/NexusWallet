package com.example.nexuswallet.feature.coin.solana.data.di

import android.content.Context
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.data.local.SolanaTransactionDao
import com.example.nexuswallet.feature.coin.solana.data.remote.HeliusApi
import com.example.nexuswallet.feature.coin.solana.data.repository.SolanaBlockchainRepositoryImpl
import com.example.nexuswallet.feature.coin.solana.data.repository.SolanaTransactionRepositoryImpl
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolanaDataModule {

    @Provides
    @Singleton
    fun provideHeliusApiKey(@ApplicationContext context: Context): String {
        return BuildConfig.HELIUS_API_KEY
    }

    // ===== HELIUS RPC (for sol4k Connection) =====
    @Provides
    @Singleton
    @Named("heliusRpcDevnet")
    fun provideHeliusRpcDevnetConnection(apiKey: String): Connection {
        return Connection("https://devnet.helius-rpc.com/?api-key=$apiKey")
    }

    @Provides
    @Singleton
    @Named("heliusRpcMainnet")
    fun provideHeliusRpcMainnetConnection(apiKey: String): Connection {
        return Connection("https://mainnet.helius-rpc.com/?api-key=$apiKey")
    }

    // ===== HELIUS REST API (Retrofit) =====
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
    fun provideHeliusApi(
        @Named("heliusApiDevnet") devnetBaseUrl: String,
        @Named("heliusApiMainnet") mainnetBaseUrl: String,
        @ApplicationContext context: Context,
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
        apiKey: String,
        logger: Logger
    ): SolanaBlockchainRepository {
        return SolanaBlockchainRepositoryImpl(
            rpcDevnetConnection = rpcDevnetConnection,
            rpcMainnetConnection = rpcMainnetConnection,
            heliusApi = heliusApi,
            apiKey = apiKey,
            logger = logger
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
        return SolanaTransactionRepositoryImpl(solanaTransactionDao, logger)
    }
}