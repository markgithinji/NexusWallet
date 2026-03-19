package com.example.nexuswallet.feature.wallet.domain.di

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GenerateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GenerateQrCodeUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetBitcoinDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetEthereumDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetSolanaDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetTransactionDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncWalletBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.ValidateMnemonicUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WalletDomainModule {

    @Provides
    @Singleton
    fun provideGenerateMnemonicUseCase(
        logger: Logger
    ): GenerateMnemonicUseCase {
        return GenerateMnemonicUseCase(
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateMnemonicUseCase(
        logger: Logger
    ): ValidateMnemonicUseCase {
        return ValidateMnemonicUseCase(
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetAllTransactionsUseCase(
        walletRepository: WalletRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        evmTransactionRepository: EVMTransactionRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        ioDispatcher: CoroutineDispatcher,
        logger: Logger
    ): GetAllTransactionsUseCase {
        return GetAllTransactionsUseCase(
            walletRepository = walletRepository,
            bitcoinTransactionRepository = bitcoinTransactionRepository,
            evmTransactionRepository = evmTransactionRepository,
            solanaTransactionRepository = solanaTransactionRepository,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            ioDispatcher = ioDispatcher,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetTransactionDetailUseCase(
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        evmTransactionRepository: EVMTransactionRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        logger: Logger
    ): GetTransactionDetailUseCase {
        return GetTransactionDetailUseCase(
            bitcoinTransactionRepository = bitcoinTransactionRepository,
            evmTransactionRepository = evmTransactionRepository,
            solanaTransactionRepository = solanaTransactionRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideCreateWalletUseCase(
        walletDataSource: WalletDataSource,
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): CreateWalletUseCase {
        return CreateWalletUseCase(
            walletDataSource = walletDataSource,
            keyStoreRepository = keyStoreRepository,
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideFormatTransactionDisplayUseCase(): FormatTransactionDisplayUseCase {
        return FormatTransactionDisplayUseCase()
    }

    @Provides
    @Singleton
    fun provideGetBitcoinDetailUseCase(
        walletRepository: WalletRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        logger: Logger
    ): GetBitcoinDetailUseCase {
        return GetBitcoinDetailUseCase(
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
        logger: Logger
    ): GetEthereumDetailUseCase {
        return GetEthereumDetailUseCase(
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
        logger: Logger
    ): GetSolanaDetailUseCase {
        return GetSolanaDetailUseCase(
            walletRepository = walletRepository,
            solanaTransactionRepository = solanaTransactionRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideSyncWalletBalancesUseCase(
        balanceDataSource: BalanceDataSource,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): SyncWalletBalancesUseCase {
        return SyncWalletBalancesUseCase(
            balanceDataSource = balanceDataSource,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGenerateQrCodeUseCase(): GenerateQrCodeUseCase {
        return GenerateQrCodeUseCase()
    }
}