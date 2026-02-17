package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BackupEntity
import com.example.nexuswallet.feature.wallet.data.model.BalanceEntity
import com.example.nexuswallet.feature.wallet.data.model.WalletEntity
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.Wallet
import com.example.nexuswallet.feature.wallet.domain.WalletBackup
import com.example.nexuswallet.feature.wallet.data.walletsrefactor.WalletBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
class WalletLocalDataSource @Inject constructor(
    private val walletDao: WalletDao,
    private val balanceDao: BalanceDao,
    private val transactionDao: TransactionDao,
    private val backupDao: BackupDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    // === Wallet Operations ===
    suspend fun saveWallet(wallet: Wallet) {
        val jsonStr = json.encodeToString(wallet)
        val entity = WalletEntity(
            id = wallet.id,
            walletJson = jsonStr,
            createdAt = wallet.createdAt
        )
        walletDao.insert(entity)
    }

    suspend fun loadWallet(walletId: String): Wallet? {
        val entity = walletDao.get(walletId) ?: return null

        return try {
            json.decodeFromString<Wallet>(entity.walletJson)
        } catch (e: Exception) {
            null
        }
    }

    fun loadAllWallets(): Flow<List<Wallet>> {
        return walletDao.getAll().map { entities ->
            entities.mapNotNull {
                try {
                    json.decodeFromString<Wallet>(it.walletJson)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    suspend fun deleteWallet(walletId: String) {
        walletDao.delete(walletId)
        balanceDao.delete(walletId)
        transactionDao.delete(walletId)
    }

    // === Balance Operations ===
    suspend fun saveWalletBalance(balance: WalletBalance) {
        val jsonStr = json.encodeToString(balance)
        val entity = BalanceEntity(
            walletId = balance.walletId,
            balanceJson = jsonStr
        )
        balanceDao.insert(entity)
    }

    suspend fun loadWalletBalance(walletId: String): WalletBalance? {
        val entity = balanceDao.get(walletId) ?: return null
        return try {
            json.decodeFromString<WalletBalance>(entity.balanceJson)
        } catch (e: Exception) {
            null
        }
    }

    // === Backup Operations ===
    suspend fun saveBackupMetadata(backup: WalletBackup) {
        val jsonStr = Json.Default.encodeToString(backup)
        val entity = BackupEntity(
            walletId = backup.walletId,
            backupJson = jsonStr
        )
        backupDao.insert(entity)
    }
}