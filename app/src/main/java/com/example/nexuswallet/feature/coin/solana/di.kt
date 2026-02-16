package com.example.nexuswallet.feature.coin.solana

import com.example.nexuswallet.feature.wallet.data.local.TransactionLocalDataSource
import com.example.nexuswallet.feature.wallet.data.repository.KeyManager
import com.example.nexuswallet.feature.wallet.data.repository.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.sol4k.Connection
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SolanaModule {

    @Provides
    @Singleton
    fun provideSolanaBlockchainRepository(
        @Named("solanaDevnet") devnetConnection: Connection,
        @Named("solanaMainnet") mainnetConnection: Connection
    ): SolanaBlockchainRepository {
        return SolanaBlockchainRepository(
            devnetConnection = devnetConnection,
            mainnetConnection = mainnetConnection
        )
    }

    @Provides
    @Singleton
    @Named("solanaDevnet")
    fun provideSolanaConnection(): Connection {
        return Connection("https://api.devnet.solana.com")
    }

    @Provides
    @Singleton
    @Named("solanaMainnet")
    fun provideSolanaMainnetConnection(): Connection {
        return Connection("https://api.mainnet-beta.solana.com")
    }
}