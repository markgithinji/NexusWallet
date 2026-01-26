package com.example.nexuswallet.feature.wallet.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.nexuswallet.feature.wallet.data.model.BackupEntity
import com.example.nexuswallet.feature.wallet.data.model.BalanceEntity
import com.example.nexuswallet.feature.wallet.data.model.MnemonicEntity
import com.example.nexuswallet.feature.wallet.data.model.SettingsEntity
import com.example.nexuswallet.feature.wallet.data.model.TransactionEntity
import com.example.nexuswallet.feature.wallet.data.model.WalletEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallet: WalletEntity)

    @Query("SELECT * FROM wallets WHERE id = :walletId")
    suspend fun get(walletId: String): WalletEntity?

    @Query("SELECT * FROM wallets ORDER BY createdAt DESC")
    fun getAll(): Flow<List<WalletEntity>>

    @Query("DELETE FROM wallets WHERE id = :walletId")
    suspend fun delete(walletId: String)

    @Query("SELECT COUNT(*) FROM wallets")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM wallets WHERE id = :walletId)")
    suspend fun exists(walletId: String): Boolean
}

@Dao
interface BalanceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(balance: BalanceEntity)

    @Query("SELECT * FROM balances WHERE walletId = :walletId")
    suspend fun get(walletId: String): BalanceEntity?

    @Update
    suspend fun update(balance: BalanceEntity)

    @Query("DELETE FROM balances WHERE walletId = :walletId")
    suspend fun delete(walletId: String)
}

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE walletId = :walletId ORDER BY updatedAt DESC")
    fun getByWallet(walletId: String): Flow<TransactionEntity?>

    @Query("DELETE FROM transactions WHERE walletId = :walletId")
    suspend fun delete(walletId: String)
}

@Dao
interface SettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(settings: SettingsEntity)

    @Query("SELECT * FROM settings WHERE walletId = :walletId")
    suspend fun get(walletId: String): SettingsEntity?

    @Update
    suspend fun update(settings: SettingsEntity)

    @Query("DELETE FROM settings WHERE walletId = :walletId")
    suspend fun delete(walletId: String)
}

@Dao
interface BackupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(backup: BackupEntity)

    @Query("SELECT * FROM backups WHERE walletId = :walletId")
    suspend fun get(walletId: String): BackupEntity?

    @Query("DELETE FROM backups WHERE walletId = :walletId")
    suspend fun delete(walletId: String)
}

@Dao
interface MnemonicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mnemonic: MnemonicEntity)

    @Query("SELECT * FROM mnemonics WHERE walletId = :walletId")
    suspend fun get(walletId: String): MnemonicEntity?

    @Query("DELETE FROM mnemonics WHERE walletId = :walletId")
    suspend fun delete(walletId: String)
}