package com.example.nexuswallet.feature.coin.bitcoin


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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BitcoinNetworkModule {

    private const val BLOCKSTREAM_MAINNET_URL = "https://blockstream.info/api/"
    private const val BLOCKSTREAM_TESTNET_URL = "https://blockstream.info/testnet/api/"

    @Provides
    @Singleton
    fun provideBitcoinJson(): Json {
        return Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Provides
    @Singleton
    @Named("bitcoinMainnet")
    fun provideBitcoinMainnetApi(
        client: OkHttpClient,
        json: Json
    ): BitcoinApi {
        return Retrofit.Builder()
            .baseUrl(BLOCKSTREAM_MAINNET_URL)
            .client(client)
            .addConverterFactory(PlainTextConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BitcoinApi::class.java)
    }

    @Provides
    @Singleton
    @Named("bitcoinTestnet")
    fun provideBitcoinTestnetApi(
        client: OkHttpClient,
        json: Json
    ): BitcoinApi {
        return Retrofit.Builder()
            .baseUrl(BLOCKSTREAM_TESTNET_URL)
            .client(client)
            .addConverterFactory(PlainTextConverterFactory())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(BitcoinApi::class.java)
    }
}