package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    // Transaction Creation
    @Provides
    @Singleton
    fun provideCreateSendTransactionUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository
    ): CreateSendTransactionUseCase {
        return CreateSendTransactionUseCase(
            walletRepository = walletRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            ethereumTransactionRepository = ethereumTransactionRepository
        )
    }

    // Signing
    @Provides
    @Singleton
    fun provideSignEthereumTransactionUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        keyManager: KeyManager,
        ethereumTransactionRepository: EthereumTransactionRepository
    ): SignEthereumTransactionUseCase {
        return SignEthereumTransactionUseCase(
            walletRepository = walletRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            keyManager = keyManager,
            ethereumTransactionRepository
        )
    }

    // Broadcasting
    @Provides
    @Singleton
    fun provideBroadcastTransactionUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository
    ): BroadcastTransactionUseCase {
        return BroadcastTransactionUseCase(
            walletRepository = walletRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            ethereumTransactionRepository
        )
    }

    // Transaction Queries
    @Provides
    @Singleton
    fun provideGetTransactionUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository
    ): GetTransactionUseCase {
        return GetTransactionUseCase(
           ethereumTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetWalletTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository
    ): GetWalletTransactionsUseCase {
        return GetWalletTransactionsUseCase(
            ethereumTransactionRepository
        )
    }

    @Provides
    @Singleton
    fun provideGetPendingTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository
    ): GetPendingTransactionsUseCase {
        return GetPendingTransactionsUseCase(
            ethereumTransactionRepository
        )
    }

    // Validation & Fee Estimation
    @Provides
    @Singleton
    fun provideValidateAddressUseCase(): ValidateAddressUseCase {
        return ValidateAddressUseCase()
    }

    @Provides
    @Singleton
    fun provideGetFeeEstimateUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository
    ): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCase(
            ethereumBlockchainRepository
        )
    }

    @Provides
    @Singleton
    fun provideEthereumTransactionDao(database: WalletDatabase): EthereumTransactionDao {
        return database.ethereumTransactionDao()
    }

    @Provides
    @Singleton
    fun provideEthereumTransactionRepository(
        ethereumTransactionDao: EthereumTransactionDao
    ): EthereumTransactionRepository {
        return EthereumTransactionRepository(ethereumTransactionDao)
    }

    @Provides
    @Singleton
    fun provideSendEthereumUseCase(
    createSendTransactionUseCase: CreateSendTransactionUseCase,
    signEthereumTransactionUseCase: SignEthereumTransactionUseCase,
    broadcastTransactionUseCase: BroadcastTransactionUseCase
    ): SendEthereumUseCase {
        return SendEthereumUseCase(
            createSendTransactionUseCase,
            signEthereumTransactionUseCase,
            broadcastTransactionUseCase
        )
    }
}