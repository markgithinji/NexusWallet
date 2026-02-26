package com.example.nexuswallet.feature.authentication.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import java.security.KeyStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthModule {
    @Provides
    @Singleton
    fun provideKeyStoreRepository(
        keyStore: KeyStore,
        ioDispatcher: CoroutineDispatcher
    ): KeyStoreRepository {
        return KeyStoreRepositoryImpl(
            keyStore = keyStore,
            ioDispatcher = ioDispatcher
        )
    }

    @Provides
    @Singleton
    fun provideSecureStorage(
        dataStore: DataStore<Preferences>
    ): SecurityPreferencesRepository {
        return SecurityPreferencesRepositoryImpl(dataStore)
    }


    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create(
            produceFile = {
                context.preferencesDataStoreFile("secure_storage")
            }
        )
    }

    @Provides
    @Singleton
    fun provideAndroidKeyStore(): KeyStore {
        return KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }
}