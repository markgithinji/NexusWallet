package com.example.nexuswallet.feature.wallet.domain.di

import com.example.nexuswallet.feature.core.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.usecase.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.ClearPinUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.GetAuthStatusUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsPinSetUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.SetBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.SetPinUseCase
import com.example.nexuswallet.feature.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.domain.usecase.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GenerateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetBitcoinDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetEthereumDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetSolanaDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.GetTransactionDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.IsSessionValidUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.SyncWalletBalancesUseCase
import com.example.nexuswallet.feature.wallet.domain.usecase.ValidateMnemonicUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetTransactionDetailUseCase(
        walletRepository: WalletRepository,
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        evmTransactionRepository: EVMTransactionRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        logger: Logger
    ): GetTransactionDetailUseCase {
        return GetTransactionDetailUseCase(
            walletRepository = walletRepository,
            bitcoinTransactionRepository = bitcoinTransactionRepository,
            evmTransactionRepository = evmTransactionRepository,
            solanaTransactionRepository = solanaTransactionRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideClearAllSecurityDataUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): ClearAllSecurityDataUseCase {
        return ClearAllSecurityDataUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            keyStoreRepository = keyStoreRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideSetBiometricEnabledUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): SetBiometricEnabledUseCase {
        return SetBiometricEnabledUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideIsBiometricEnabledUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): IsBiometricEnabledUseCase {
        return IsBiometricEnabledUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideSetPinUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): SetPinUseCase {
        return SetPinUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideIsPinSetUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): IsPinSetUseCase {
        return IsPinSetUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideClearPinUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): ClearPinUseCase {
        return ClearPinUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetAuthStatusUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): GetAuthStatusUseCase {
        return GetAuthStatusUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideIsSessionValidUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): IsSessionValidUseCase {
        return IsSessionValidUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
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
        formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
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
        formatTransactionDisplayUseCase: FormatTransactionDisplayUseCase,
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
        walletDataSource: WalletDataSource,
        balanceDataSource: BalanceDataSource,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        evmBlockchainRepository: EVMBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        logger: Logger
    ): SyncWalletBalancesUseCase {
        return SyncWalletBalancesUseCase(
            walletDataSource = walletDataSource,
            balanceDataSource = balanceDataSource,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            evmBlockchainRepository = evmBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            logger = logger
        )
    }
}