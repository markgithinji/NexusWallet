package com.example.nexuswallet.feature.settings.data.di

import com.example.nexuswallet.feature.settings.data.repository.BackupRepositoryImpl
import com.example.nexuswallet.feature.settings.data.repository.SettingsRepositoryImpl
import com.example.nexuswallet.feature.settings.domain.repository.BackupRepository
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
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
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(
        impl: BackupRepositoryImpl
    ): BackupRepository
}
