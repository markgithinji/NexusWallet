package com.example.nexuswallet.feature.bitcoin.data.di

import com.example.nexuswallet.feature.bitcoin.data.local.BitcoinTransactionDao
import com.example.nexuswallet.feature.bitcoin.data.remote.api.BitcoinApi
import com.example.nexuswallet.feature.bitcoin.data.remote.api.PlainTextConverterFactory
import com.example.nexuswallet.feature.bitcoin.data.repository.BitcoinBlockchainRepositoryImpl
import com.example.nexuswallet.feature.bitcoin.data.repository.BitcoinTransactionRepositoryImpl
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BitcoinNetworkModule {

    @Binds
    @Singleton
    abstract fun bindBitcoinBlockchainRepository(
        impl: BitcoinBlockchainRepositoryImpl
    ): BitcoinBlockchainRepository

    @Binds
    @Singleton
    abstract fun bindBitcoinTransactionRepository(
        impl: BitcoinTransactionRepositoryImpl
    ): BitcoinTransactionRepository

    companion object {
        private const val BLOCKSTREAM_MAINNET_URL = "https://blockstream.info/api/"
        private const val BLOCKSTREAM_TESTNET_URL = "https://blockstream.info/testnet/api/"

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

        @Provides
        @Singleton
        fun provideBitcoinTransactionDao(database: WalletDatabase): BitcoinTransactionDao {
            return database.bitcoinTransactionDao()
        }
    }
}
