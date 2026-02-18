package com.example.nexuswallet.feature.wallet.data.local

import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.BitcoinCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.EthereumCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.SolanaCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCBalanceDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.USDCCoinDao
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.mapToWalletBalance
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toDomain
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

class WalletLocalDataSource @Inject constructor(
    private val walletDao: WalletDao,
    private val bitcoinCoinDao: BitcoinCoinDao,
    private val ethereumCoinDao: EthereumCoinDao,
    private val solanaCoinDao: SolanaCoinDao,
    private val usdcCoinDao: USDCCoinDao,
    private val bitcoinBalanceDao: BitcoinBalanceDao,
    private val ethereumBalanceDao: EthereumBalanceDao,
    private val solanaBalanceDao: SolanaBalanceDao,
    private val usdcBalanceDao: USDCBalanceDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    // === Wallet Operations ===
    suspend fun saveWallet(wallet: Wallet) {
        // Save main wallet entity
        walletDao.insert(wallet.toEntity())

        // Save coins
        wallet.bitcoin?.let {
            bitcoinCoinDao.insert(it.toEntity(wallet.id))
        }

        wallet.ethereum?.let {
            ethereumCoinDao.insert(it.toEntity(wallet.id))
        }

        wallet.solana?.let {
            solanaCoinDao.insert(it.toEntity(wallet.id))
        }

        wallet.usdc?.let {
            usdcCoinDao.insert(it.toEntity(wallet.id))
        }
    }

    suspend fun loadWallet(walletId: String): Wallet? {
        val walletEntity = walletDao.get(walletId) ?: return null

        return walletEntity.toDomain(
            bitcoinCoin = bitcoinCoinDao.getByWalletId(walletId)?.toDomain(),
            ethereumCoin = ethereumCoinDao.getByWalletId(walletId)?.toDomain(),
            solanaCoin = solanaCoinDao.getByWalletId(walletId)?.toDomain(),
            usdcCoin = usdcCoinDao.getByWalletId(walletId)?.toDomain()
        )
    }

    fun loadAllWallets(): Flow<List<Wallet>> {
        return walletDao.getAll().map { entities ->
            entities.map { entity ->
                // For each wallet entity, load its complete data with all coins
                entity.toDomain(
                    bitcoinCoin = bitcoinCoinDao.getByWalletId(entity.id)?.toDomain(),
                    ethereumCoin = ethereumCoinDao.getByWalletId(entity.id)?.toDomain(),
                    solanaCoin = solanaCoinDao.getByWalletId(entity.id)?.toDomain(),
                    usdcCoin = usdcCoinDao.getByWalletId(entity.id)?.toDomain()
                )
            }
        }
    }

    suspend fun deleteWallet(walletId: String) {
        walletDao.delete(walletId)
        // Coins and balances are deleted automatically via CASCADE foreign keys
        // No need to manually delete from coin/balance tables
    }

    // === Balance Operations ===
    suspend fun saveWalletBalance(balance: WalletBalance) {
        // Save individual balances
        balance.bitcoin?.let {
            bitcoinBalanceDao.insert(it.toEntity(balance.walletId))
        }

        balance.ethereum?.let {
            ethereumBalanceDao.insert(it.toEntity(balance.walletId))
        }

        balance.solana?.let {
            solanaBalanceDao.insert(it.toEntity(balance.walletId))
        }

        balance.usdc?.let {
            usdcBalanceDao.insert(it.toEntity(balance.walletId))
        }
    }

    suspend fun loadWalletBalance(walletId: String): WalletBalance? {
        return mapToWalletBalance(
            walletId = walletId,
            bitcoinBalance = bitcoinBalanceDao.getByWalletId(walletId),
            ethereumBalance = ethereumBalanceDao.getByWalletId(walletId),
            solanaBalance = solanaBalanceDao.getByWalletId(walletId),
            usdcBalance = usdcBalanceDao.getByWalletId(walletId)
        )
    }
}