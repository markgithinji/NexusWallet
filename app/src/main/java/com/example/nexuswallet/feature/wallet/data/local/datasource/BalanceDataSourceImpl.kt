package com.example.nexuswallet.feature.wallet.data.local.datasource

import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.mapper.toDomain
import com.example.nexuswallet.feature.wallet.data.local.mapper.toEntity
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceDataSourceImpl @Inject constructor(
    private val bitcoinCoinDao: BitcoinCoinDao,
    private val solanaCoinDao: SolanaCoinDao,
    private val bitcoinBalanceDao: BitcoinBalanceDao,
    private val solanaBalanceDao: SolanaBalanceDao,
    private val evmBalanceDao: EVMBalanceDao
) : BalanceDataSource {

    override suspend fun saveWalletBalance(balance: WalletBalance) {
        // Save Bitcoin balances
        balance.bitcoinBalances.forEach { (network, bitcoinBalance) ->
            val bitcoinCoin = bitcoinCoinDao.getByAddress(bitcoinBalance.address)
            if (bitcoinCoin != null) {
                bitcoinBalanceDao.insert(bitcoinBalance.toEntity(bitcoinCoin.id))
            }
        }

        // Save Solana balances
        balance.solanaBalances.forEach { (network, solanaBalance) ->
            val solanaCoin = solanaCoinDao.getByAddress(solanaBalance.address)
            if (solanaCoin != null) {
                solanaBalanceDao.insert(solanaBalance.toEntity(solanaCoin.id))
            }
        }

        // Save EVM balances
        balance.evmBalances.forEach { evmBalance ->
            evmBalanceDao.insert(evmBalance.toEntity(balance.walletId))
        }
    }

    override suspend fun loadWalletBalance(walletId: String): WalletBalance? {
        // Load Bitcoin balances
        val bitcoinBalanceEntities = bitcoinBalanceDao.getByWalletId(walletId)
        val bitcoinBalances = mutableMapOf<BitcoinNetwork, BitcoinBalance>()

        bitcoinBalanceEntities.forEach { balanceEntity ->
            val coin = bitcoinCoinDao.getById(balanceEntity.coinId)
            coin?.network?.let { network ->
                bitcoinBalances[network] = balanceEntity.toDomain()
            }
        }

        // Load Solana balances
        val solanaBalanceEntities = solanaBalanceDao.getByWalletId(walletId)
        val solanaBalances = mutableMapOf<SolanaNetwork, SolanaBalance>()

        solanaBalanceEntities.forEach { balanceEntity ->
            val coin = solanaCoinDao.getById(balanceEntity.coinId)
            coin?.network?.let { network ->
                solanaBalances[network] = balanceEntity.toDomain()
            }
        }

        // Load EVM balances
        val evmBalances = evmBalanceDao.getByWalletId(walletId).map { entity ->
            entity.toDomain()
        }

        return if (bitcoinBalances.isEmpty() && solanaBalances.isEmpty() && evmBalances.isEmpty()) {
            null
        } else {
            WalletBalance(
                walletId = walletId,
                lastUpdated = System.currentTimeMillis(),
                bitcoinBalances = bitcoinBalances,
                solanaBalances = solanaBalances,
                evmBalances = evmBalances
            )
        }
    }
}