package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
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
object DetailUseCaseModule {

    @Provides
    @Singleton
    fun provideGetBitcoinDetailUseCase(
        walletRepository: WalletRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
        logger: Logger
    ): GetBitcoinDetailUseCase {
        return GetBitcoinDetailUseCaseImpl(
            walletRepository = walletRepository,
            bitcoinTransactionRepository = bitcoinTransactionRepository,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            formatTransactionDisplayUseCase = formatTransactionDisplayUseCase,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetEthereumDetailUseCase(
        walletRepository: WalletRepository,
        evmTransactionRepository: EVMTransactionRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
        logger: Logger
    ): GetEthereumDetailUseCase {
        return GetEthereumDetailUseCaseImpl(
            walletRepository = walletRepository,
            evmTransactionRepository = evmTransactionRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            formatTransactionDisplayUseCase = formatTransactionDisplayUseCase,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetSolanaDetailUseCase(
        walletRepository: WalletRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
        logger: Logger
    ): GetSolanaDetailUseCase {
        return GetSolanaDetailUseCaseImpl(
            walletRepository = walletRepository,
            solanaTransactionRepository = solanaTransactionRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            formatTransactionDisplayUseCase = formatTransactionDisplayUseCase,
            logger = logger
        )
    }
}