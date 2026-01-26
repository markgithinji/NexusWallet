package com.example.nexuswallet.feature.wallet.domain

import android.content.Context
import android.content.SharedPreferences
import android.media.CamcorderProfile.getAll
import com.example.nexuswallet.feature.wallet.data.local.WalletDatabase
import com.example.nexuswallet.feature.wallet.data.model.BackupEntity
import com.example.nexuswallet.feature.wallet.data.model.BalanceEntity
import com.example.nexuswallet.feature.wallet.data.model.MnemonicEntity
import com.example.nexuswallet.feature.wallet.data.model.SettingsEntity
import com.example.nexuswallet.feature.wallet.data.model.TransactionEntity
import com.example.nexuswallet.feature.wallet.data.model.WalletEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.nio.file.Files.delete

class WalletStorage(context: Context) {
    private val database = WalletDatabase.getDatabase(context)
    private val walletDao = database.walletDao()
    private val balanceDao = database.balanceDao()
    private val transactionDao = database.transactionDao()
    private val settingsDao = database.settingsDao()
    private val backupDao = database.backupDao()
    private val mnemonicDao = database.mnemonicDao()
    private val json = Json { ignoreUnknownKeys = true }

    // === Wallet Operations ===
    suspend fun saveWallet(wallet: CryptoWallet) {
        val jsonStr = when (wallet) {
            is BitcoinWallet -> json.encodeToString(wallet)
            is EthereumWallet -> json.encodeToString(wallet)
            is MultiChainWallet -> json.encodeToString(wallet)
            is SolanaWallet -> json.encodeToString(wallet)
            else -> throw IllegalArgumentException("Unknown wallet type")
        }

        val entity = WalletEntity(
            id = wallet.id,
            walletJson = jsonStr,
            createdAt = wallet.createdAt
        )
        walletDao.insert(entity)
    }

    suspend fun loadWallet(walletId: String): CryptoWallet? {
        val entity = walletDao.get(walletId) ?: return null
        return tryDeserializeWallet(entity.walletJson)
    }

    fun loadAllWallets(): Flow<List<CryptoWallet>> {
        return walletDao.getAll().map { entities ->
            entities.mapNotNull { tryDeserializeWallet(it.walletJson) }
        }
    }

    suspend fun deleteWallet(walletId: String) {
        // Delete all related data
        walletDao.delete(walletId)
        balanceDao.delete(walletId)
        transactionDao.delete(walletId)
        settingsDao.delete(walletId)
        backupDao.delete(walletId)
        mnemonicDao.delete(walletId)
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

    // === Transaction Operations ===
    suspend fun saveTransactions(walletId: String, transactions: List<Transaction>) {
        val jsonStr = json.encodeToString(transactions)
        val entity = TransactionEntity(
            walletId = walletId,
            transactionsJson = jsonStr
        )
        transactionDao.insert(entity)
    }

    fun loadTransactions(walletId: String): Flow<List<Transaction>> {
        return transactionDao.getByWallet(walletId).map { entity ->
            if (entity == null) emptyList() else {
                try {
                    json.decodeFromString<List<Transaction>>(entity.transactionsJson)
                } catch (e: Exception) {
                    emptyList()
                }
            }
        }
    }

    // === Settings Operations ===
    suspend fun saveSettings(settings: WalletSettings) {
        val jsonStr = json.encodeToString(settings)
        val entity = SettingsEntity(
            walletId = settings.walletId,
            settingsJson = jsonStr
        )
        settingsDao.insert(entity)
    }

    suspend fun loadSettings(walletId: String): WalletSettings? {
        val entity = settingsDao.get(walletId) ?: return null
        return try {
            json.decodeFromString<WalletSettings>(entity.settingsJson)
        } catch (e: Exception) {
            null
        }
    }

    // === Mnemonic Operations ===
    suspend fun saveEncryptedMnemonic(walletId: String, encryptedData: String) {
        val entity = MnemonicEntity(
            walletId = walletId,
            encryptedData = encryptedData
        )
        mnemonicDao.insert(entity)
    }

    suspend fun loadEncryptedMnemonic(walletId: String): String? {
        return mnemonicDao.get(walletId)?.encryptedData
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

    suspend fun loadBackupMetadata(walletId: String): WalletBackup? {
        val entity = backupDao.get(walletId) ?: return null
        return try {
            Json.Default.decodeFromString<WalletBackup>(entity.backupJson)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun deleteBackupMetadata(walletId: String) {
        backupDao.delete(walletId)
    }

    // === Helper Methods ===
    suspend fun clearAll() {
        database.walletDao().run {
            getAll().first().forEach { delete(it.id) }
        }
    }

    suspend fun walletExists(walletId: String): Boolean {
        return walletDao.exists(walletId)
    }

    suspend fun getAllWalletIds(): List<String> {
        return walletDao.getAll().first().map { it.id }
    }

    suspend fun getWalletCount(): Int {
        return walletDao.count()
    }

    suspend fun hasWallets(): Boolean {
        return walletDao.count() > 0
    }

    private fun tryDeserializeWallet(jsonStr: String): CryptoWallet? {
        return listOf(
            { json.decodeFromString<BitcoinWallet>(jsonStr) },
            { json.decodeFromString<EthereumWallet>(jsonStr) },
            { json.decodeFromString<MultiChainWallet>(jsonStr) },
            { json.decodeFromString<SolanaWallet>(jsonStr) }
        ).firstNotNullOfOrNull { runCatching { it() }.getOrNull() }
    }
}