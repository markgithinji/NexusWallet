package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
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
    fun provideSessionManager(securityPreferencesRepository: SecurityPreferencesRepository): SessionManager {
        return SessionManager(securityPreferencesRepository)
    }

    // Coin creation usecases
    @Provides
    @Singleton
    fun provideCreateBitcoinCoinUseCase(): CreateBitcoinCoinUseCase {
        return CreateBitcoinCoinUseCase()
    }

    @Provides
    @Singleton
    fun provideCreateEthereumCoinUseCase(): CreateEthereumCoinUseCase {
        return CreateEthereumCoinUseCase()
    }

    @Provides
    @Singleton
    fun provideCreateSolanaCoinUseCase(): CreateSolanaCoinUseCase {
        return CreateSolanaCoinUseCase()
    }

    @Provides
    @Singleton
    fun provideDerivePrivateKeyFromMnemonicUseCase(): DerivePrivateKeyFromMnemonicUseCase {
        return DerivePrivateKeyFromMnemonicUseCase()
    }

    // Mnemonic usecases
    @Provides
    @Singleton
    fun provideGenerateMnemonicUseCase(): GenerateMnemonicUseCase = GenerateMnemonicUseCase()

    @Provides
    @Singleton
    fun provideValidateMnemonicUseCase(): ValidateMnemonicUseCase = ValidateMnemonicUseCase()

    // Security usecases
    @Provides
    @Singleton
    fun provideRetrieveMnemonicUseCase(
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository
    ): RetrieveMnemonicUseCase {
        return RetrieveMnemonicUseCase(keyStoreRepository, securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideSecureMnemonicUseCase(
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository
    ): SecureMnemonicUseCase {
        return SecureMnemonicUseCase(keyStoreRepository, securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideStorePrivateKeyUseCase(
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository
    ): StorePrivateKeyUseCase {
        return StorePrivateKeyUseCase(
            keyStoreRepository,
            securityPreferencesRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetPrivateKeyForSigningUseCase(
        keyStoreRepository: KeyStoreRepository,
        securityPreferencesRepository: SecurityPreferencesRepository
    ): GetPrivateKeyForSigningUseCase {
        return GetPrivateKeyForSigningUseCase(keyStoreRepository, securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideHasPrivateKeyUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository
    ): HasPrivateKeyUseCase {
        return HasPrivateKeyUseCase(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideClearAllSecurityDataUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        sessionManager: SessionManager
    ): ClearAllSecurityDataUseCase {
        return ClearAllSecurityDataUseCase(
            securityPreferencesRepository,
            keyStoreRepository,
            sessionManager
        )
    }

    @Provides
    @Singleton
    fun provideIsKeyStoreAvailableUseCase(
        keyStoreRepository: KeyStoreRepository
    ): IsKeyStoreAvailableUseCase {
        return IsKeyStoreAvailableUseCase(keyStoreRepository)
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
        isPinSetUseCase: IsPinSetUseCase,
        isBiometricEnabledUseCase: IsBiometricEnabledUseCase
    ): GetAvailableAuthMethodsUseCase {
        return GetAvailableAuthMethodsUseCase(isPinSetUseCase, isBiometricEnabledUseCase)
    }

    @Provides
    @Singleton
    fun provideIsAnyAuthEnabledUseCase(
        isPinSetUseCase: IsPinSetUseCase,
        isBiometricEnabledUseCase: IsBiometricEnabledUseCase
    ): IsAnyAuthEnabledUseCase {
        return IsAnyAuthEnabledUseCase(isPinSetUseCase, isBiometricEnabledUseCase)
    }

    // Session management usecases
    @Provides
    @Singleton
    fun provideSetSessionTimeoutUseCase(sessionManager: SessionManager): SetSessionTimeoutUseCase {
        return SetSessionTimeoutUseCase(sessionManager)
    }

    @Provides
    @Singleton
    fun provideIsSessionValidUseCase(sessionManager: SessionManager): IsSessionValidUseCase {
        return IsSessionValidUseCase(sessionManager)
    }

    @Provides
    @Singleton
    fun provideIsAuthenticationRequiredUseCase(sessionManager: SessionManager): IsAuthenticationRequiredUseCase {
        return IsAuthenticationRequiredUseCase(sessionManager)
    }

    @Provides
    @Singleton
    fun provideRecordAuthenticationUseCase(sessionManager: SessionManager): RecordAuthenticationUseCase {
        return RecordAuthenticationUseCase(sessionManager)
    }

    @Provides
    @Singleton
    fun provideClearSessionUseCase(sessionManager: SessionManager): ClearSessionUseCase {
        return ClearSessionUseCase(sessionManager)
    }

    // Wallet creation usecase
    @Provides
    @Singleton
    fun provideCreateWalletUseCase(
        walletLocalDataSource: WalletLocalDataSource,
        secureMnemonicUseCase: SecureMnemonicUseCase,
        storePrivateKeyUseCase: StorePrivateKeyUseCase,
        createBitcoinCoinUseCase: CreateBitcoinCoinUseCase,
        createEthereumCoinUseCase: CreateEthereumCoinUseCase,
        createSolanaCoinUseCase: CreateSolanaCoinUseCase,
        derivePrivateKeyFromMnemonicUseCase: DerivePrivateKeyFromMnemonicUseCase
    ): CreateWalletUseCase {
        return CreateWalletUseCase(
            walletLocalDataSource,
            secureMnemonicUseCase,
            storePrivateKeyUseCase,
            createBitcoinCoinUseCase,
            createEthereumCoinUseCase,
            createSolanaCoinUseCase,
            derivePrivateKeyFromMnemonicUseCase
        )
    }
}