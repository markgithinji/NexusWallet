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
    fun provideKeyValidator(): KeyValidator = KeyValidator()

    @Provides
    @Singleton
    fun providePinManager(securityPreferencesRepository: SecurityPreferencesRepository): PinManager {
        return PinManager(securityPreferencesRepository)
    }

    @Provides
    @Singleton
    fun provideSessionManager(securityPreferencesRepository: SecurityPreferencesRepository): SessionManager {
        return SessionManager(securityPreferencesRepository)
    }

    // Coin creation usecases
    @Provides
    @Singleton
    fun provideCreateBitcoinCoinUseCase(keyValidator: KeyValidator): CreateBitcoinCoinUseCase {
        return CreateBitcoinCoinUseCase(keyValidator)
    }

    @Provides
    @Singleton
    fun provideCreateEthereumCoinUseCase(keyValidator: KeyValidator): CreateEthereumCoinUseCase {
        return CreateEthereumCoinUseCase(keyValidator)
    }

    @Provides
    @Singleton
    fun provideCreateSolanaCoinUseCase(keyValidator: KeyValidator): CreateSolanaCoinUseCase {
        return CreateSolanaCoinUseCase(keyValidator)
    }

    @Provides
    @Singleton
    fun provideDerivePrivateKeyFromMnemonicUseCase(keyValidator: KeyValidator): DerivePrivateKeyFromMnemonicUseCase {
        return DerivePrivateKeyFromMnemonicUseCase(keyValidator)
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
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyValidator: KeyValidator
    ): StorePrivateKeyUseCase {
        return StorePrivateKeyUseCase(
            keyStoreRepository,
            securityPreferencesRepository,
            keyValidator
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
    fun provideSetPinUseCase(pinManager: PinManager): SetPinUseCase {
        return SetPinUseCase(pinManager)
    }

    @Provides
    @Singleton
    fun provideVerifyPinUseCase(pinManager: PinManager): VerifyPinUseCase {
        return VerifyPinUseCase(pinManager)
    }

    @Provides
    @Singleton
    fun provideIsPinSetUseCase(pinManager: PinManager): IsPinSetUseCase {
        return IsPinSetUseCase(pinManager)
    }

    @Provides
    @Singleton
    fun provideClearPinUseCase(pinManager: PinManager): ClearPinUseCase {
        return ClearPinUseCase(pinManager)
    }

    @Provides
    @Singleton
    fun provideGetAvailableAuthMethodsUseCase(
        pinManager: PinManager,
        isBiometricEnabledUseCase: IsBiometricEnabledUseCase
    ): GetAvailableAuthMethodsUseCase {
        return GetAvailableAuthMethodsUseCase(pinManager, isBiometricEnabledUseCase)
    }

    @Provides
    @Singleton
    fun provideIsAnyAuthEnabledUseCase(
        pinManager: PinManager,
        isBiometricEnabledUseCase: IsBiometricEnabledUseCase
    ): IsAnyAuthEnabledUseCase {
        return IsAnyAuthEnabledUseCase(pinManager, isBiometricEnabledUseCase)
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