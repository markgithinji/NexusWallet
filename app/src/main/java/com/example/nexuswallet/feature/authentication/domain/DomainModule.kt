package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepositoryImpl
import com.example.nexuswallet.feature.wallet.domain.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun provideVerifyPinUseCase(
        securityPreferencesRepository: SecurityPreferencesRepository,
        logger: Logger
    ): VerifyPinUseCase {
        return VerifyPinUseCaseImpl(
            securityPreferencesRepository = securityPreferencesRepository,
            logger = logger
        )
    }
}