package com.example.nexuswallet.feature.wallet.data.local.datasource

import androidx.room.withTransaction
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.mapper.toDomain
import com.example.nexuswallet.feature.wallet.data.local.mapper.toEntity
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.datasource.WalletDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletDataSourceImpl @Inject constructor(
    private val walletDatabase: WalletDatabase,
    private val walletDao: WalletDao,
    private val bitcoinCoinDao: BitcoinCoinDao,
    private val solanaCoinDao: SolanaCoinDao,
    private val evmTokenDao: EVMTokenDao,
    private val splTokenDao: SPLTokenDao
) : WalletDataSource {

    override suspend fun saveWallet(wallet: Wallet) {
        walletDatabase.withTransaction {
            walletDao.insert(wallet.toEntity())

            wallet.bitcoinCoins.forEach { coin ->
                bitcoinCoinDao.insert(coin.toEntity(wallet.id))
            }

            wallet.solanaCoins.forEach { coin ->
                val solanaCoinEntity = coin.toEntity(wallet.id)
                solanaCoinDao.insert(solanaCoinEntity)
                coin.splTokens.forEach { splToken ->
                    splTokenDao.insert(splToken.toEntity(solanaCoinEntity.id))
                }
            }

            wallet.evmTokens.forEach { token ->
                evmTokenDao.insert(token.toEntity(wallet.id))
            }
        }
    }

    override suspend fun loadWallet(walletId: String): Wallet? {
        val walletEntity = walletDao.get(walletId) ?: return null

        val bitcoinCoins = bitcoinCoinDao.getByWalletId(walletId)
            .map { it.toDomain() }

        val solanaCoins = solanaCoinDao.getByWalletId(walletId)
            .map { solanaEntity ->
                solanaEntity.toDomain(
                    splTokens = splTokenDao.getBySolanaCoinId(solanaEntity.id).map { splEntity ->
                        splEntity.toDomain()
                    }
                )
            }

        val evmTokens = evmTokenDao.getByWalletId(walletId).map { entity ->
            entity.toDomain()
        }

        return walletEntity.toDomain(
            bitcoinCoins = bitcoinCoins,
            solanaCoins = solanaCoins,
            evmTokens = evmTokens
        )
    }

    override fun loadAllWallets(): Flow<List<Wallet>> {
        return combine(
            walletDao.getAll(),
            bitcoinCoinDao.observeAll(),
            solanaCoinDao.observeAll(),
            evmTokenDao.observeAll(),
            splTokenDao.observeAll()
        ) { walletEntities, btc, sol, evm, spl ->
            walletEntities.map { entity ->
                entity.toDomain(
                    bitcoinCoins = btc.filter { it.walletId == entity.id }.map { it.toDomain() },
                    solanaCoins = sol.filter { it.walletId == entity.id }.map { solEntity ->
                        solEntity.toDomain(
                            splTokens = spl.filter { it.solanaCoinId == solEntity.id }.map { it.toDomain() }
                        )
                    },
                    evmTokens = evm.filter { it.walletId == entity.id }.map { it.toDomain() }
                )
            }
        }
    }

    override suspend fun deleteWallet(walletId: String) {
        walletDao.delete(walletId)
    }

    override suspend fun updateWalletName(walletId: String, newName: String) {
        walletDao.updateName(walletId, newName)
    }
}



