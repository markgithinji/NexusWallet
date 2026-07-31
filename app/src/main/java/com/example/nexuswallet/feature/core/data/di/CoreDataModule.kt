package com.example.nexuswallet.feature.core.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.nexuswallet.feature.core.data.repository.KeyStoreRepositoryImpl
import com.example.nexuswallet.feature.core.data.repository.VaultRepositoryImpl
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
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
object CoreDataModule {

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
    fun provideVaultRepository(
        dataStore: DataStore<Preferences>
    ): VaultRepository {
        return VaultRepositoryImpl(dataStore)
    }

    @Provides
    @Singleton
    fun provideKeyStoreRepository(
        keyStore: KeyStore,
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher
    ): KeyStoreRepository {
        return KeyStoreRepositoryImpl(
            keyStore = keyStore,
            context = context,
            ioDispatcher = ioDispatcher
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
