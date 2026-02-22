package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.authentication.data.repository.KeyStoreRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideKeyStoreRepository(
    ): KeyStoreRepository {
        return KeyStoreRepository()
    }
}