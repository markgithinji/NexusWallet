package com.example.nexuswallet.feature.authentication.domain.di

import com.example.nexuswallet.feature.core.data.util.PinHasher
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
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