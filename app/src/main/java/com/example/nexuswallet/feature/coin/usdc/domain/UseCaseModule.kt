package com.example.nexuswallet.feature.coin.usdc.domain

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumTransactionRepository
import com.example.nexuswallet.feature.coin.ethereum.GetEthereumWalletUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetEthereumWalletUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetFeeEstimateUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetPendingTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetPendingTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetTransactionUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.GetWalletTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.GetWalletTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.SendEthereumUseCase
import com.example.nexuswallet.feature.coin.ethereum.SendEthereumUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCase
import com.example.nexuswallet.feature.coin.ethereum.SyncEthereumTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.ethereum.ValidateEthereumSendUseCase
import com.example.nexuswallet.feature.coin.ethereum.ValidateEthereumSendUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCTransactionRepository
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule {

    @Provides
    @Singleton
    fun provideSyncUSDTransactionsUseCase(
        usdcBlockchainRepository: USDCBlockchainRepository,
        usdcTransactionRepository: USDCTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncUSDTransactionsUseCase {
        return SyncUSDTransactionsUseCaseImpl(
            usdcBlockchainRepository,
            usdcTransactionRepository,
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetUSDCWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetUSDCWalletUseCase {
        return GetUSDCWalletUseCaseImpl(
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetUSDCBalanceUseCase(
        walletRepository: WalletRepository,
        usdcBlockchainRepository: USDCBlockchainRepository,
        logger: Logger
    ): GetUSDCBalanceUseCase {
        return GetUSDCBalanceUseCaseImpl(
            walletRepository,
            usdcBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetETHBalanceForGasUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        logger: Logger
    ): GetETHBalanceForGasUseCase {
        return GetETHBalanceForGasUseCaseImpl(
            walletRepository,
            ethereumBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetUSDCFeeEstimateUseCase(
        blockchainRepository: USDCBlockchainRepository,
        logger: Logger
    ): GetUSDCFeeEstimateUseCase {
        return GetUSDCFeeEstimateUseCaseImpl(
            blockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateUSDCFormUseCase(
        logger: Logger
    ): ValidateUSDCFormUseCase {
        return ValidateUSDCFormUseCaseImpl(logger)
    }

    @Provides
    @Singleton
    fun provideSendUSDCUseCase(
        walletRepository: WalletRepository,
        usdcBlockchainRepository: USDCBlockchainRepository,
        usdcTransactionRepository: USDCTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): SendUSDCUseCase {
        return SendUSDCUseCaseImpl(
            walletRepository,
            usdcBlockchainRepository,
            usdcTransactionRepository,
            securityPreferencesRepository,
            keyStoreRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSyncEthereumTransactionsUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        walletRepository: WalletRepository,
        logger: Logger
    ): SyncEthereumTransactionsUseCase {
        return SyncEthereumTransactionsUseCaseImpl(
            ethereumBlockchainRepository,
            ethereumTransactionRepository,
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetTransactionUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository,
        logger: Logger
    ): GetTransactionUseCase {
        return GetTransactionUseCaseImpl(
            ethereumTransactionRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetWalletTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository,
        logger: Logger
    ): GetWalletTransactionsUseCase {
        return GetWalletTransactionsUseCaseImpl(
            ethereumTransactionRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetPendingTransactionsUseCase(
        ethereumTransactionRepository: EthereumTransactionRepository,
        logger: Logger
    ): GetPendingTransactionsUseCase {
        return GetPendingTransactionsUseCaseImpl(
            ethereumTransactionRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideValidateEthereumSendUseCase(
        getFeeEstimateUseCase: GetFeeEstimateUseCase,
        logger: Logger
    ): ValidateEthereumSendUseCase {
        return ValidateEthereumSendUseCaseImpl(
            getFeeEstimateUseCase,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetFeeEstimateUseCase(
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        logger: Logger
    ): GetFeeEstimateUseCase {
        return GetFeeEstimateUseCaseImpl(
            ethereumBlockchainRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideGetEthereumWalletUseCase(
        walletRepository: WalletRepository,
        logger: Logger
    ): GetEthereumWalletUseCase {
        return GetEthereumWalletUseCaseImpl(
            walletRepository,
            logger
        )
    }

    @Provides
    @Singleton
    fun provideSendEthereumUseCase(
        walletRepository: WalletRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        ethereumTransactionRepository: EthereumTransactionRepository,
        securityPreferencesRepository: SecurityPreferencesRepository,
        keyStoreRepository: KeyStoreRepository,
        logger: Logger
    ): SendEthereumUseCase {
        return SendEthereumUseCaseImpl(
            walletRepository,
            ethereumBlockchainRepository,
            ethereumTransactionRepository,
            securityPreferencesRepository,
            keyStoreRepository,
            logger
        )
    }
}