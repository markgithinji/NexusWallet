package com.example.nexuswallet.feature.wallet.data.test

import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.test.kettest.KeyStorageTestRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SepoliaModule {

    @Provides
    @Singleton
    fun provideSepoliaRepository(
        keyManager: KeyManager,
        walletRepository: WalletRepository
    ): SepoliaRepository {
        return SepoliaRepository(
            keyManager,
            walletRepository = walletRepository
        )
    }

    @Provides
    @Singleton
    fun provideKeyStorageTestRepository(
        keyManager: KeyManager,
    ): KeyStorageTestRepository {
        return KeyStorageTestRepository(keyManager)
    }
}