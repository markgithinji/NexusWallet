package com.example.nexuswallet.feature.coin.usdc

import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EtherscanApiService
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetETHBalanceForGasUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCBalanceUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCFeeEstimateUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCFeeEstimateUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCWalletUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.GetUSDCWalletUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.domain.SendUSDCUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SendUSDCUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.domain.SyncUSDTransactionsUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.SyncUSDTransactionsUseCaseImpl
import com.example.nexuswallet.feature.coin.usdc.domain.USDCTransactionRepository
import com.example.nexuswallet.feature.coin.usdc.domain.ValidateUSDCFormUseCase
import com.example.nexuswallet.feature.coin.usdc.domain.ValidateUSDCFormUseCaseImpl
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.domain.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class USDCModule {

    @Provides
    @Singleton
    fun provideWeb3jFactory(): Web3jFactory = Web3jFactory()

    @Provides
    @Singleton
    fun provideUSDCBlockchainRepository(
        etherscanApi: EtherscanApiService,
        web3jFactory: Web3jFactory,
        logger: Logger
    ): USDCBlockchainRepository {
        return USDCBlockchainRepositoryImpl(etherscanApi, web3jFactory)
    }

    @Provides
    @Singleton
    fun provideUSDCTransactionDao(database: WalletDatabase): USDCTransactionDao {
        return database.usdcTransactionDao()
    }

    @Provides
    @Singleton
    fun provideUSDCTransactionRepository(
        usdcTransactionDao: USDCTransactionDao
    ): USDCTransactionRepository {
        return USDCTransactionRepositoryImpl(usdcTransactionDao)
    }

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
        usdcBlockchainRepository: USDCBlockchainRepository,
        logger: Logger
    ): GetUSDCFeeEstimateUseCase {
        return GetUSDCFeeEstimateUseCaseImpl(
            usdcBlockchainRepository,
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
}