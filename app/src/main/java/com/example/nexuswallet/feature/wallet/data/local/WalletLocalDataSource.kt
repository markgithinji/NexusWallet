package com.example.nexuswallet.feature.wallet.data.local

import android.content.Context
import android.util.Log
import com.example.nexuswallet.feature.wallet.data.model.BackupEntity
import com.example.nexuswallet.feature.wallet.data.model.BalanceEntity
import com.example.nexuswallet.feature.wallet.data.model.MnemonicEntity
import com.example.nexuswallet.feature.wallet.data.model.SendTransactionDao
import com.example.nexuswallet.feature.wallet.data.model.SettingsEntity
import com.example.nexuswallet.feature.wallet.data.model.TransactionEntity
import com.example.nexuswallet.feature.wallet.data.model.WalletEntity
import com.example.nexuswallet.feature.wallet.domain.BitcoinWallet
import com.example.nexuswallet.feature.wallet.domain.CryptoWallet
import com.example.nexuswallet.feature.wallet.domain.EthereumWallet
import com.example.nexuswallet.feature.wallet.domain.MultiChainWallet
import com.example.nexuswallet.feature.wallet.domain.SolanaWallet
import com.example.nexuswallet.feature.wallet.domain.Transaction
import com.example.nexuswallet.feature.wallet.domain.USDCWallet
import com.example.nexuswallet.feature.wallet.domain.WalletBackup
import com.example.nexuswallet.feature.wallet.domain.WalletBalance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject

class WalletLocalDataSource @Inject constructor(
    private val walletDao: WalletDao,
    private val balanceDao: BalanceDao,
    private val transactionDao: TransactionDao,
    private val settingsDao: SettingsDao,
    private val backupDao: BackupDao,
    private val mnemonicDao: MnemonicDao,
    private val sendTransactionDao: SendTransactionDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    // === Wallet Operations ===
    suspend fun saveWallet(wallet: CryptoWallet) {
        val jsonStr = when (wallet) {
            is BitcoinWallet -> json.encodeToString(wallet)
            is EthereumWallet -> json.encodeToString(wallet)
            is MultiChainWallet -> json.encodeToString(wallet)
            is SolanaWallet -> json.encodeToString(wallet)
            is USDCWallet -> json.encodeToString(wallet)
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
        Log.d("WalletStorage", "Loading wallet with ID: $walletId")
        val entity = walletDao.get(walletId) ?: return null.also {
            Log.d("WalletStorage", "Wallet not found in database")
        }

        return try {
            val wallet = tryDeserializeWallet(entity.walletJson)
            Log.d("WalletStorage", "Wallet loaded: ${wallet?.name}")
            wallet
        } catch (e: Exception) {
            Log.e("WalletStorage", "Error deserializing wallet: ${e.message}")
            null
        }
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

    // === Backup Operations ===
    suspend fun saveBackupMetadata(backup: WalletBackup) {
        val jsonStr = Json.Default.encodeToString(backup)
        val entity = BackupEntity(
            walletId = backup.walletId,
            backupJson = jsonStr
        )
        backupDao.insert(entity)
    }

    private fun tryDeserializeWallet(jsonStr: String): CryptoWallet? {
        Log.d("WalletStorage", "Trying to deserialize wallet JSON")

        val result = listOf(
            { json.decodeFromString<BitcoinWallet>(jsonStr).also {
                Log.d("WalletStorage", "Successfully deserialized as BitcoinWallet")
            } },
            { json.decodeFromString<EthereumWallet>(jsonStr).also {
                Log.d("WalletStorage", "Successfully deserialized as EthereumWallet")
            } },
            { json.decodeFromString<USDCWallet>(jsonStr).also {
                Log.d("WalletStorage", "Successfully deserialized as USDCWallet")
            } },
            { json.decodeFromString<SolanaWallet>(jsonStr).also {
                Log.d("WalletStorage", "Successfully deserialized as SolanaWallet")
            } },
            { json.decodeFromString<MultiChainWallet>(jsonStr).also {
                Log.d("WalletStorage", "Successfully deserialized as MultiChainWallet")
            } }
        ).firstNotNullOfOrNull { runCatching { it() }.getOrNull() }

        Log.d("WalletStorage", "Deserialization result type: ${result?.javaClass?.simpleName}")
        return result
    }
}