package com.example.nexuswallet.feature.wallet.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val walletJson: String,
    val createdAt: Long,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "balances",
    indices = [Index(value = ["walletId"], unique = true)]
)
data class BalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val balanceJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["walletId"])]
)
data class TransactionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val transactionsJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "settings",
    indices = [Index(value = ["walletId"], unique = true)]
)
data class SettingsEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val settingsJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "backups",
    indices = [Index(value = ["walletId"], unique = true)]
)
data class BackupEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val backupJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mnemonics",
    indices = [Index(value = ["walletId"], unique = true)]
)
data class MnemonicEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val encryptedData: String,
    val updatedAt: Long = System.currentTimeMillis()
)