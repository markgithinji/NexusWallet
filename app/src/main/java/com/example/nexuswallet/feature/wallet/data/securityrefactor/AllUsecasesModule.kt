package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.authentication.domain.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.authentication.domain.VerifyPinUseCase
import com.example.nexuswallet.feature.settings.ui.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.settings.ui.ClearPinUseCase
import com.example.nexuswallet.feature.settings.ui.GetAuthStatusUseCase
import com.example.nexuswallet.feature.settings.ui.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.ui.IsPinSetUseCase
import com.example.nexuswallet.feature.settings.ui.SetBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.ui.SetPinUseCase
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AllUsecasesModule {

    // Mnemonic usecases
    @Provides
    @Singleton
    fun provideGenerateMnemonicUseCase(): GenerateMnemonicUseCase = GenerateMnemonicUseCase()

    @Provides
    @Singleton
    fun provideValidateMnemonicUseCase(): ValidateMnemonicUseCase = ValidateMnemonicUseCase()

    @Provides
    @Singleton
    fun provideClearAllSecurityDataUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
    ): ClearAllSecurityDataUseCase {
        return ClearAllSecurityDataUseCase(
            securityPreferencesRepository,
            keyStoreRepository
        )
    }

    // PIN and Biometric usecases
    @Provides
    @Singleton
    fun provideSetBiometricEnabledUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository
    ): SetBiometricEnabledUseCase {
        return SetBiometricEnabledUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideIsBiometricEnabledUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository
    ): IsBiometricEnabledUseCase {
        return IsBiometricEnabledUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideSetPinUseCase(securityPreferencesRepository: SecurityPreferencesRepository): SetPinUseCase {
        return SetPinUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideVerifyPinUseCase(securityPreferencesRepository: SecurityPreferencesRepository): VerifyPinUseCase {
        return VerifyPinUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideIsPinSetUseCase(securityPreferencesRepository: SecurityPreferencesRepository): IsPinSetUseCase {
        return IsPinSetUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideClearPinUseCase(securityPreferencesRepository: SecurityPreferencesRepository): ClearPinUseCase {
        return ClearPinUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideGetAvailableAuthMethodsUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository
    ): GetAuthStatusUseCase {
        return GetAuthStatusUseCase (securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideIsSessionValidUseCase(securityPreferencesRepository: SecurityPreferencesRepository): IsSessionValidUseCase {
        return IsSessionValidUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideRecordAuthenticationUseCase(securityPreferencesRepository: SecurityPreferencesRepository): RecordAuthenticationUseCase {
        return RecordAuthenticationUseCase(securityPreferencesRepository)
    }

    // Wallet creation usecase
    @Provides
    @Singleton
    fun provideCreateWalletUseCase(
        walletLocalDataSource: WalletLocalDataSource,
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository
    ): CreateWalletUseCase {
        return CreateWalletUseCase(
            walletLocalDataSource,
            keyStoreRepository,
            securityPreferencesRepository
        )
    }
}