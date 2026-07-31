package com.example.nexuswallet.feature.settings.data.di

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.example.nexuswallet.feature.settings.data.repository.SecurityRepositoryImpl
import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SettingsDataModule {

    @Provides
    @Singleton
    fun provideSecurityRepository(
        dataStore: DataStore<Preferences>
    ): SecurityRepository {
        return SecurityRepositoryImpl(dataStore)
    }
}
