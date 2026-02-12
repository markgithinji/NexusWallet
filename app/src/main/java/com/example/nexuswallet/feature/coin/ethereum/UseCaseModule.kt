package com.example.nexuswallet.feature.coin.ethereum

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
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
        transactionLocalDataSource: TransactionLocalDataSource
    ): CreateSendTransactionUseCase {
        return CreateSendTransactionUseCase(
            walletRepository = walletRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            transactionLocalDataSource = transactionLocalDataSource
        )
    }

    // Signing
    @Provides
    @Singleton
    fun provideSignEthereumTransactionUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        keyManager: KeyManager,
        transactionLocalDataSource: TransactionLocalDataSource
    ): SignEthereumTransactionUseCase {
        return SignEthereumTransactionUseCase(
            walletRepository = walletRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            keyManager = keyManager,
            transactionLocalDataSource = transactionLocalDataSource
        )
    }

    // Broadcasting
    @Provides
    @Singleton
    fun provideBroadcastTransactionUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        transactionLocalDataSource: TransactionLocalDataSource
    ): BroadcastTransactionUseCase {
        return BroadcastTransactionUseCase(
            walletRepository = walletRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            transactionLocalDataSource = transactionLocalDataSource
        )
    }

    // Transaction Queries
    @Provides
    @Singleton
    fun provideGetTransactionUseCase(
        transactionLocalDataSource: TransactionLocalDataSource
    ): GetTransactionUseCase {
        return GetTransactionUseCase(
            transactionLocalDataSource = transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideGetWalletTransactionsUseCase(
        transactionLocalDataSource: TransactionLocalDataSource
    ): GetWalletTransactionsUseCase {
        return GetWalletTransactionsUseCase(
            transactionLocalDataSource = transactionLocalDataSource
        )
    }

    @Provides
    @Singleton
    fun provideGetPendingTransactionsUseCase(
        transactionLocalDataSource: TransactionLocalDataSource
    ): GetPendingTransactionsUseCase {
        return GetPendingTransactionsUseCase(
            transactionLocalDataSource = transactionLocalDataSource
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
    fun provideGetFeeEstimateUseCase(): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCase()
    }
}