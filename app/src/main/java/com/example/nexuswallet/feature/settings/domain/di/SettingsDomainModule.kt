package com.example.nexuswallet.feature.settings.domain.di

import com.example.nexuswallet.feature.core.data.util.PinHasher
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsDomainModule {

    @Provides
    @Singleton
    fun provideSetPinUseCase(
        securityRepository: SecurityRepository,
        pinHasher: PinHasher,
        logger: Logger
    ): SetPinUseCase {
        return SetPinUseCase(
            securityRepository = securityRepository,
            pinHasher = pinHasher,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideIsPinSetUseCase(
        securityRepository: SecurityRepository,
        logger: Logger
    ): IsPinSetUseCase {
        return IsPinSetUseCase(
            securityRepository = securityRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideClearPinUseCase(
        securityRepository: SecurityRepository,
        logger: Logger
    ): ClearPinUseCase {
        return ClearPinUseCase(
            securityRepository = securityRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideSetBiometricEnabledUseCase(
        securityRepository: SecurityRepository,
        logger: Logger
    ): SetBiometricEnabledUseCase {
        return SetBiometricEnabledUseCase(
            securityRepository = securityRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideIsBiometricEnabledUseCase(
        securityRepository: SecurityRepository,
        logger: Logger
    ): IsBiometricEnabledUseCase {
        return IsBiometricEnabledUseCase(
            securityRepository = securityRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideGetAuthStatusUseCase(
        securityRepository: SecurityRepository,
        logger: Logger
    ): GetAuthStatusUseCase {
        return GetAuthStatusUseCase(
            securityRepository = securityRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideClearAllSecurityDataUseCase(
        securityRepository: SecurityRepository,
        vaultRepository: VaultRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): ClearAllSecurityDataUseCase {
        return ClearAllSecurityDataUseCase(
            securityRepository = securityRepository,
            vaultRepository = vaultRepository,
            keyStoreRepository = keyStoreRepository,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideVerifyPinUseCase(
        securityRepository: SecurityRepository,
        pinHasher: PinHasher,
        logger: Logger
    ): VerifyPinUseCase {
        return VerifyPinUseCase(
            securityRepository = securityRepository,
            pinHasher = pinHasher,
            logger = logger
        )
    }

    @Provides
    @Singleton
    fun provideRecordAuthenticationUseCase(
        securityRepository: SecurityRepository,
        logger: Logger
    ): RecordAuthenticationUseCase {
        return RecordAuthenticationUseCase(
            securityRepository = securityRepository,
            logger = logger
        )
    }
}