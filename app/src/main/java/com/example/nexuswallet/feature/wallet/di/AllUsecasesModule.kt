package com.example.nexuswallet.feature.wallet.di

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.authentication.domain.usecase.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.usecase.GetAuthStatusUseCase
import com.example.nexuswallet.feature.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.wallet.data.securityrefactor.GenerateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.data.securityrefactor.ValidateMnemonicUseCase
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCaseImpl
import com.example.nexuswallet.feature.wallet.domain.usecase.GetTransactionDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.WalletDataSource
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AllUsecasesModule {

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
    fun provideRecordAuthenticationUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): RecordAuthenticationUseCase {
        return RecordAuthenticationUseCase(
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
    fun provideFormatTransactionDisplayUseCase(): FormatTransactionDisplayUseCaseImpl {
        return FormatTransactionDisplayUseCase()
    }
}