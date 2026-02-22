package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.logging.HttpLoggingInterceptor
import org.sol4k.Connection
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolanaModule {

    @Provides
    @Singleton
    fun provideSolanaRpcService(json: Json): SolanaRpcService {
        val client = Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.devnet.solana.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(SolanaRpcService::class.java)
    }

    @Provides
    @Singleton
    fun provideSolanaBlockchainRepository(
        @Named("solanaDevnet") devnetConnection: Connection,
        @Named("solanaMainnet") mainnetConnection: Connection,
        solanaRpcService: SolanaRpcService
    ): SolanaBlockchainRepository {
        return SolanaBlockchainRepository(
            devnetConnection = devnetConnection,
            mainnetConnection = mainnetConnection,
            solanaRpcService = solanaRpcService
        )
    }

    @Provides
    @Singleton
    @Named("solanaDevnet")
    fun provideSolanaConnection(): Connection {
        return Connection("https://api.devnet.solana.com")
    }

    @Provides
    @Singleton
    @Named("solanaMainnet")
    fun provideSolanaMainnetConnection(): Connection {
        return Connection("https://api.mainnet-beta.solana.com")
    }
}