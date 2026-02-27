package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.authentication.domain.RecordAuthenticationUseCaseImpl
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.bitcoin.data.BitcoinTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.data.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.solana.domain.SolanaTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.ui.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.settings.ui.ClearAllSecurityDataUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.ClearPinUseCase
import com.example.nexuswallet.feature.settings.ui.ClearPinUseCaseImpl
import com.example.nexuswallet.feature.settings.ui.GetAuthStatusUseCase
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
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
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
    fun provideGetMnemonicUseCase(
        bitcoinTransactionRepository: BitcoinTransactionRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        solanaTransactionRepository: SolanaTransactionRepository,
        usdcTransactionRepository: USDCTransactionRepository,
        logger: Logger
    ): GetAllTransactionsUseCase {
        return GetAllTransactionsUseCaseImpl(
            bitcoinTransactionRepository,
            ethereumTransactionRepository,
            solanaTransactionRepository,
            usdcTransactionRepository,
            logger
        )
    }


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