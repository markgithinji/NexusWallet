package com.example.nexuswallet.feature.settings.domain.di


import com.example.nexuswallet.feature.authentication.data.util.PinHasher
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.settings.domain.usecase.ClearAllSecurityDataUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.ClearPinUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.GetAuthStatusUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.IsPinSetUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.SetBiometricEnabledUseCase
import com.example.nexuswallet.feature.settings.domain.usecase.SetPinUseCase
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
        securityPreferencesRepository: SecurityPreferencesRepository,
        pinHasher: PinHasher,
        logger: Logger
    ): SetPinUseCase {
        return SetPinUseCase(
            securityPreferencesRepository = securityPreferencesRepository,
            pinHasher = pinHasher,
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
}