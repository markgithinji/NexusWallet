package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.usecase.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.authentication.domain.RecordAuthenticationUseCaseImpl
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.bitcoin.domain.repository.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.domain.repository.EVMTransactionRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.domain.repository.SolanaTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.ui.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.settings.ui.ClearAllSecurityDataUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.ClearPinUseCase
import com.example.nexuswallet.feature.settings.ui.ClearPinUseCaseImpl
import com.example.nexuswallet.feature.settings.domain.usecase.GetAuthStatusUseCase
import com.example.nexuswallet.feature.settings.ui.GetAuthStatusUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.ui.IsBiometricEnabledUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.IsPinSetUseCase
import com.example.nexuswallet.feature.settings.ui.IsPinSetUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.SetBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.ui.SetBiometricEnabledUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.SetPinUseCase
import com.example.nexuswallet.feature.settings.ui.SetPinUseCaseImpl
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.FormatTransactionDisplayUseCaseImpl
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.GetAllTransactionsUseCaseImpl
import com.example.nexuswallet.feature.wallet.domain.CreateWalletUseCase
import com.example.nexuswallet.feature.wallet.domain.FormatTransactionDisplayUseCase
import com.example.nexuswallet.feature.wallet.domain.GetAllTransactionsUseCase
import com.example.nexuswallet.feature.wallet.domain.GetTransactionDetailUseCase
import com.example.nexuswallet.feature.wallet.domain.GetTransactionDetailUseCaseImpl
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
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
    ): GenerateMnemonicUseCase = GenerateMnemonicUseCaseImpl(
        logger
    )

    @Provides
    @Singleton
    fun provideValidateMnemonicUseCase(
        logger: Logger
    ): ValidateMnemonicUseCase = ValidateMnemonicUseCaseImpl(
        logger
    )

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
        return GetAllTransactionsUseCaseImpl(
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
    fun provideGetTransactionDetailUseCase(
        impl: GetTransactionDetailUseCaseImpl
    ): GetTransactionDetailUseCase = impl

    @Provides
    @Singleton
    fun provideClearAllSecurityDataUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): ClearAllSecurityDataUseCase {
        return ClearAllSecurityDataUseCaseImpl(
            securityPreferencesRepository,
            keyStoreRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSetBiometricEnabledUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): SetBiometricEnabledUseCase {
        return SetBiometricEnabledUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideIsBiometricEnabledUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): IsBiometricEnabledUseCase {
        return IsBiometricEnabledUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSetPinUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): SetPinUseCase {
        return SetPinUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideIsPinSetUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): IsPinSetUseCase {
        return IsPinSetUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideClearPinUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): ClearPinUseCase {
        return ClearPinUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetAuthStatusUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): GetAuthStatusUseCase {
        return GetAuthStatusUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideIsSessionValidUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): IsSessionValidUseCase {
        return IsSessionValidUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideRecordAuthenticationUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): RecordAuthenticationUseCase {
        return RecordAuthenticationUseCaseImpl(
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideCreateWalletUseCase(
        walletLocalDataSource: WalletLocalDataSource,
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): CreateWalletUseCase {
        return CreateWalletUseCaseImpl(
            walletLocalDataSource,
            keyStoreRepository,
            securityPreferencesRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideVerifyPinUseCase(): FormatTransactionDisplayUseCase {
        return FormatTransactionDisplayUseCaseImpl()
    }
}