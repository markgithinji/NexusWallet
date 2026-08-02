package com.example.nexuswallet.feature.settings.domain.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SettingsDomainModule {
    // All UseCases in this package use @Inject constructor and @Singleton
    // Hilt will automatically provide them without explicit @Provides methods.
}
