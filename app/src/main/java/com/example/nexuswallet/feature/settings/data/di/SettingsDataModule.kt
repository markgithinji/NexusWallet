package com.example.nexuswallet.feature.settings.data.di

import com.example.nexuswallet.feature.settings.data.repository.SecurityRepositoryImpl
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsDataModule {

    @Binds
    @Singleton
    abstract fun bindSecurityRepository(
        impl: SecurityRepositoryImpl
    ): SecurityRepository
}
