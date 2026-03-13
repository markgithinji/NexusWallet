package com.example.nexuswallet.feature.authentication.domain.di

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.authentication.domain.usecase.RecordAuthenticationUseCase
import com.example.nexuswallet.feature.authentication.domain.usecase.VerifyPinUseCase
import com.example.nexuswallet.feature.logging.Logger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthenticationDomainModule {

    @Provides
    @Singleton
    fun provideVerifyPinUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): VerifyPinUseCase {
        return VerifyPinUseCase(
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
}