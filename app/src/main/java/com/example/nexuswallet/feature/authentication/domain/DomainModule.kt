package com.example.nexuswallet.feature.authentication.domain

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Provides
    @Singleton
    abstract fun provideVerifyPinUseCase(
        verifyPinUseCaseImpl: VerifyPinUseCaseImpl
    ): VerifyPinUseCase

    @Provides
    @Singleton
    abstract fun provideRecordAuthenticationUseCase(
        recordAuthenticationUseCaseImpl: RecordAuthenticationUseCaseImpl
    ): RecordAuthenticationUseCase
}