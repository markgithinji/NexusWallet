package com.example.nexuswallet.feature.wallet.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import java.util.UUID

@Entity(tableName = "wallets")
data class WalletEntity(
    @PrimaryKey val id: String,
    val name: String,
    val mnemonicHash: String,
    val createdAt: Long,
    val isBackedUp: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
)

// Bitcoin coin table
@Entity(
    tableName = "bitcoin_coins",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class BitcoinCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: BitcoinNetwork,
    val xpub: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// Ethereum coin table
@Entity(
    tableName = "ethereum_coins",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class EthereumCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: EthereumNetwork,
    val updatedAt: Long = System.currentTimeMillis()
)

// Solana coin table
@Entity(
    tableName = "solana_coins",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class SolanaCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// USDC coin table
@Entity(
    tableName = "usdc_coins",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class USDCCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val network: String,
    val contractAddress: String,
    val updatedAt: Long = System.currentTimeMillis()
)

// Bitcoin balance table
@Entity(
    tableName = "bitcoin_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class BitcoinBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val satoshis: String,
    val btc: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

// Ethereum balance table
@Entity(
    tableName = "ethereum_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class EthereumBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val wei: String,
    val eth: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

// Solana balance table
@Entity(
    tableName = "solana_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class SolanaBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val lamports: String,
    val sol: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

// USDC balance table
@Entity(
    tableName = "usdc_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["walletId"], unique = true)]
)
data class USDCBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val amount: String,
    val amountDecimal: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)