package com.example.nexuswallet.feature.solana.data.di

import com.example.nexuswallet.feature.solana.data.local.SolanaTransactionDao
import com.example.nexuswallet.feature.solana.data.remote.HeliusApi
import com.example.nexuswallet.feature.solana.data.remote.HeliusInterceptor
import com.example.nexuswallet.feature.solana.data.repository.SolanaBlockchainRepositoryImpl
import com.example.nexuswallet.feature.solana.data.repository.SolanaTransactionRepositoryImpl
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import dagger.Binds
import dagger.Module
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
import com.example.nexuswallet.BuildConfig

@Module
@InstallIn(SingletonComponent::class)
abstract class SolanaDataModule {

    @Binds
    @Singleton
    abstract fun bindSolanaBlockchainRepository(
        impl: SolanaBlockchainRepositoryImpl
    ): SolanaBlockchainRepository

    @Binds
    @Singleton
    abstract fun bindSolanaTransactionRepository(
        impl: SolanaTransactionRepositoryImpl
    ): SolanaTransactionRepository

    companion object {
        @Provides
        @Singleton
        @Named("helius_api_key")
        fun provideHeliusApiKey(): String {
            return BuildConfig.HELIUS_API_KEY
        }

        @Provides
        @Singleton
        @Named("helius_okhttp")
        fun provideHeliusOkHttpClient(
            heliusInterceptor: HeliusInterceptor,
            okHttpClient: OkHttpClient
        ): OkHttpClient {
            return okHttpClient.newBuilder()
                .addInterceptor(heliusInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        @Named("helius_rpc_devnet")
        fun provideHeliusRpcDevnetConnection(
            @Named("helius_api_key") apiKey: String
        ): Connection {
            val url = "https://devnet.helius-rpc.com/?api-key=$apiKey"
            return Connection(url)
        }

        @Provides
        @Singleton
        @Named("helius_rpc_mainnet")
        fun provideHeliusRpcMainnetConnection(
            @Named("helius_api_key") apiKey: String
        ): Connection {
            val url = "https://mainnet.helius-rpc.com/?api-key=$apiKey"
            return Connection(url)
        }

        @Provides
        @Singleton
        @Named("helius_api_devnet")
        fun provideHeliusDevnetApi(
            @Named("helius_okhttp") okHttpClient: OkHttpClient,
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
        @Named("helius_api_mainnet")
        fun provideHeliusMainnetApi(
            @Named("helius_okhttp") okHttpClient: OkHttpClient,
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
        fun provideSolanaTransactionDao(database: WalletDatabase): SolanaTransactionDao {
            return database.solanaTransactionDao()
        }
    }
}
