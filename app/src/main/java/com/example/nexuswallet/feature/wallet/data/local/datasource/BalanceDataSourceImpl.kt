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
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
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
            saveBitcoinBalance(balance.walletId, network, bitcoinBalance)
        }

        // Save Solana balances
        balance.solanaBalances.forEach { (network, solanaBalance) ->
            saveSolanaBalance(balance.walletId, network, solanaBalance)
        }

        // Save EVM balances
        saveEVMBalances(balance.walletId, balance.evmBalances)
    }

    override suspend fun saveBitcoinBalance(
        walletId: String,
        network: BitcoinNetwork,
        balance: BitcoinBalance
    ) {
        val bitcoinCoin = bitcoinCoinDao.getByAddressAndNetwork(balance.address, network)
        if (bitcoinCoin != null) {
            bitcoinBalanceDao.insert(balance.toEntity(bitcoinCoin.id))
        }
    }

    override suspend fun saveSolanaBalance(
        walletId: String,
        network: SolanaNetwork,
        balance: SolanaBalance
    ) {
        val solanaCoin = solanaCoinDao.getByAddressAndNetwork(balance.address, network)
        if (solanaCoin != null) {
            solanaBalanceDao.insert(balance.toEntity(solanaCoin.id))
        }
    }

    override suspend fun saveEVMBalances(walletId: String, balances: List<EVMBalance>) {
        balances.forEach { evmBalance ->
            evmBalanceDao.insert(evmBalance.toEntity(walletId))
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