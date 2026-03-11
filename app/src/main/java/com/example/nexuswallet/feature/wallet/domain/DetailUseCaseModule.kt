package com.example.nexuswallet.feature.wallet.domain

import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetBitcoinDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetEthereumDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetEthereumDetailUseCaseImpl
import com.example.nexuswallet.feature.wallet.domain.usecase.GetSolanaDetailUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
            logger = logger
        )
    }
}