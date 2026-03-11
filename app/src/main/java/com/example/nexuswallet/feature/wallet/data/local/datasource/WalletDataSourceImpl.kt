package com.example.nexuswallet.feature.wallet.data.local.datasource

import com.example.nexuswallet.feature.wallet.data.local.dao.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.EVMTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SPLTokenDao
import com.example.nexuswallet.feature.wallet.data.local.dao.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.local.dao.WalletDao
import com.example.nexuswallet.feature.wallet.data.local.mapper.toDomain
import com.example.nexuswallet.feature.wallet.data.local.mapper.toEntity
import com.example.nexuswallet.feature.wallet.domain.model.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WalletDataSourceImpl @Inject constructor(
    private val walletDao: WalletDao,
    private val bitcoinCoinDao: BitcoinCoinDao,
    private val solanaCoinDao: SolanaCoinDao,
    private val evmTokenDao: EVMTokenDao,
    private val splTokenDao: SPLTokenDao
) : WalletDataSource {

    override suspend fun saveWallet(wallet: Wallet) {
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
        return walletDao.getAll().map { entities ->
            entities.map { entity ->
                val bitcoinCoins = bitcoinCoinDao.getByWalletId(entity.id)
                    .map { it.toDomain() }

                val solanaCoins = solanaCoinDao.getByWalletId(entity.id)
                    .map { solanaEntity ->
                        solanaEntity.toDomain(
                            splTokens = splTokenDao.getBySolanaCoinId(solanaEntity.id)
                                .map { splEntity ->
                                    splEntity.toDomain()
                                }
                        )
                    }

                val evmTokens = evmTokenDao.getByWalletId(entity.id).map { tokenEntity ->
                    tokenEntity.toDomain()
                }

                entity.toDomain(
                    bitcoinCoins = bitcoinCoins,
                    solanaCoins = solanaCoins,
                    evmTokens = evmTokens
                )
            }
        }
    }

    override suspend fun deleteWallet(walletId: String) {
        walletDao.delete(walletId)
    }
}



