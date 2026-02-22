package com.example.nexuswallet.feature.wallet.di

import android.content.Context
import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinBlockchainRepository
import com.example.nexuswallet.feature.coin.ethereum.EthereumBlockchainRepository
import com.example.nexuswallet.feature.coin.solana.SolanaBlockchainRepository
import com.example.nexuswallet.feature.coin.usdc.USDCBlockchainRepository
import com.example.nexuswallet.feature.wallet.data.local.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoinDao
import com.example.nexuswallet.feature.wallet.domain.SyncWalletBalancesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideWalletDatabase(@ApplicationContext context: Context): WalletDatabase {
        return WalletDatabase.getDatabase(context)
    }

    // === Wallet DAOs ===
    @Provides
    @Singleton
    fun provideWalletDao(database: WalletDatabase): WalletDao {
        return database.walletDao()
    }

    @Provides
    @Singleton
    fun provideBitcoinCoinDao(database: WalletDatabase): BitcoinCoinDao {
        return database.bitcoinCoinDao()
    }

    @Provides
    @Singleton
    fun provideEthereumCoinDao(database: WalletDatabase): EthereumCoinDao {
        return database.ethereumCoinDao()
    }

    @Provides
    @Singleton
    fun provideSolanaCoinDao(database: WalletDatabase): SolanaCoinDao {
        return database.solanaCoinDao()
    }

    @Provides
    @Singleton
    fun provideUSDCCoinDao(database: WalletDatabase): USDCCoinDao {
        return database.usdcCoinDao()
    }

    // === Balance DAOs ===
    @Provides
    @Singleton
    fun provideBitcoinBalanceDao(database: WalletDatabase): BitcoinBalanceDao {
        return database.bitcoinBalanceDao()
    }

    @Provides
    @Singleton
    fun provideEthereumBalanceDao(database: WalletDatabase): EthereumBalanceDao {
        return database.ethereumBalanceDao()
    }

    @Provides
    @Singleton
    fun provideSolanaBalanceDao(database: WalletDatabase): SolanaBalanceDao {
        return database.solanaBalanceDao()
    }

    @Provides
    @Singleton
    fun provideUSDCBalanceDao(database: WalletDatabase): USDCBalanceDao {
        return database.usdcBalanceDao()
    }

    // === Data Sources ===
    @Provides
    @Singleton
    fun provideWalletLocalDataSource(
        walletDao: WalletDao,
        bitcoinCoinDao: BitcoinCoinDao,
        ethereumCoinDao: EthereumCoinDao,
        solanaCoinDao: SolanaCoinDao,
        usdcCoinDao: USDCCoinDao,
        bitcoinBalanceDao: BitcoinBalanceDao,
        ethereumBalanceDao: EthereumBalanceDao,
        solanaBalanceDao: SolanaBalanceDao,
        usdcBalanceDao: USDCBalanceDao,
    ): WalletLocalDataSource {
        return WalletLocalDataSource(
            walletDao = walletDao,
            bitcoinCoinDao = bitcoinCoinDao,
            ethereumCoinDao = ethereumCoinDao,
            solanaCoinDao = solanaCoinDao,
            usdcCoinDao = usdcCoinDao,
            bitcoinBalanceDao = bitcoinBalanceDao,
            ethereumBalanceDao = ethereumBalanceDao,
            solanaBalanceDao = solanaBalanceDao,
            usdcBalanceDao = usdcBalanceDao
        )
    }

    @Provides
    @Singleton
    fun provideWalletRepository(
        localDataSource: WalletLocalDataSource
    ): WalletRepository {
        return WalletRepository(
            localDataSource = localDataSource
        )
    }

    // === Security ===
    @Provides
    @Singleton
    fun provideSecureStorage(
        @ApplicationContext context: Context,
    ): SecurityPreferencesRepository {
        return SecurityPreferencesRepository(context)
    }

    // === Blockchain ===
    @Provides
    @Singleton
    fun provideWeb3j(): Web3j {
        return Web3j.build(
            HttpService("https://mainnet.infura.io/v3/demo") // TODO: Use key
        )
    }

    // === Use Cases ===
    @Provides
    @Singleton
    fun provideSyncWalletBalancesUseCase(
        localDataSource: WalletLocalDataSource,
        bitcoinBlockchainRepository: BitcoinBlockchainRepository,
        ethereumBlockchainRepository: EthereumBlockchainRepository,
        solanaBlockchainRepository: SolanaBlockchainRepository,
        usdcBlockchainRepository: USDCBlockchainRepository
    ): SyncWalletBalancesUseCase {
        return SyncWalletBalancesUseCase(
            localDataSource = localDataSource,
            bitcoinBlockchainRepository = bitcoinBlockchainRepository,
            ethereumBlockchainRepository = ethereumBlockchainRepository,
            solanaBlockchainRepository = solanaBlockchainRepository,
            usdcBlockchainRepository = usdcBlockchainRepository
        )
    }
}