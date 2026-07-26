package com.example.nexuswallet.feature.wallet.data.local.datasource

import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.BitcoinCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.EVMBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaBalanceEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.SolanaCoinEntity
import com.example.nexuswallet.feature.wallet.data.local.entity.WalletEntity
import com.example.nexuswallet.feature.wallet.data.local.mapper.toDomain
import com.example.nexuswallet.feature.wallet.data.local.mapper.toEntity
import com.example.nexuswallet.feature.wallet.domain.datasource.BalanceDataSource
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.WalletBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BalanceDataSourceImpl @Inject constructor(
    private val walletDao: WalletDao,
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

    override fun observeWalletBalance(walletId: String): Flow<WalletBalance?> {
        return combine(
            bitcoinBalanceDao.observeByWalletId(walletId),
            solanaBalanceDao.observeByWalletId(walletId),
            evmBalanceDao.observeByWalletId(walletId),
            bitcoinCoinDao.observeByWalletId(walletId),
            solanaCoinDao.observeByWalletId(walletId)
        ) { btcBalances, solBalances, evmBalances, btcCoins, solCoins ->
            val btcMap = btcBalances.mapNotNull { balanceEntity ->
                val network = btcCoins.find { it.id == balanceEntity.coinId }?.network
                network?.let { it to balanceEntity.toDomain() }
            }.toMap()

            val solMap = solBalances.mapNotNull { balanceEntity ->
                val network = solCoins.find { it.id == balanceEntity.coinId }?.network
                network?.let { it to balanceEntity.toDomain() }
            }.toMap()

            val evmList = evmBalances.map { it.toDomain() }

            if (btcMap.isEmpty() && solMap.isEmpty() && evmList.isEmpty()) {
                null
            } else {
                WalletBalance(
                    walletId = walletId,
                    lastUpdated = System.currentTimeMillis(),
                    bitcoinBalances = btcMap,
                    solanaBalances = solMap,
                    evmBalances = evmList
                )
            }
        }
    }

    override fun observeAllBalances(): Flow<Map<String, WalletBalance>> {
        return combine(
            bitcoinBalanceDao.observeAll(),
            solanaBalanceDao.observeAll(),
            evmBalanceDao.observeAll(),
            bitcoinCoinDao.observeAll(),
            solanaCoinDao.observeAll(),
            walletDao.getAll()
        ) { flows ->
            val btcBalances = flows[0] as List<BitcoinBalanceEntity>
            val solBalances = flows[1] as List<SolanaBalanceEntity>
            val evmBalances = flows[2] as List<EVMBalanceEntity>
            val btcCoins = flows[3] as List<BitcoinCoinEntity>
            val solCoins = flows[4] as List<SolanaCoinEntity>
            val wallets = flows[5] as List<WalletEntity>

            wallets.associate { walletEntity ->
                val walletId = walletEntity.id

                val btcMap = btcBalances.mapNotNull { balanceEntity ->
                    val coin = btcCoins.find { it.id == balanceEntity.coinId && it.walletId == walletId }
                    coin?.network?.let { it to balanceEntity.toDomain() }
                }.toMap()

                val solMap = solBalances.mapNotNull { balanceEntity ->
                    val coin = solCoins.find { it.id == balanceEntity.coinId && it.walletId == walletId }
                    coin?.network?.let { it to balanceEntity.toDomain() }
                }.toMap()

                val evmList = evmBalances.filter { it.walletId == walletId }.map { it.toDomain() }

                walletId to WalletBalance(
                    walletId = walletId,
                    lastUpdated = System.currentTimeMillis(),
                    bitcoinBalances = btcMap,
                    solanaBalances = solMap,
                    evmBalances = evmList
                )
            }
        }
    }
}