package com.example.nexuswallet.feature.wallet.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
    indices = [Index(value = ["walletId"])]
)
data class BitcoinCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: String,
    val xpub: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "bitcoin_balances",
    foreignKeys = [
        ForeignKey(
            entity = BitcoinCoinEntity::class,
            parentColumns = ["id"],
            childColumns = ["coinId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["coinId"], unique = true)]
)
data class BitcoinBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val coinId: String,
    val address: String,
    val satoshis: String,
    val btc: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

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
    indices = [Index(value = ["walletId"])]
)
data class SolanaCoinEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "solana_balances",
    foreignKeys = [
        ForeignKey(
            entity = SolanaCoinEntity::class,
            parentColumns = ["id"],
            childColumns = ["coinId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["coinId"], unique = true)]
)
data class SolanaBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val coinId: String,
    val address: String,
    val lamports: String,
    val sol: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "spl_tokens",
    foreignKeys = [
        ForeignKey(
            entity = SolanaCoinEntity::class,
            parentColumns = ["id"],
            childColumns = ["solanaCoinId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["solanaCoinId", "mintAddress"], unique = true)]
)
data class SPLTokenEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val solanaCoinId: String,
    val mintAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "evm_tokens",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["walletId"]),
        Index(value = ["walletId", "contractAddress", "network"], unique = true),
        Index(value = ["externalId"])
    ]
)
data class EVMTokenEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val address: String,
    val publicKey: String,
    val derivationPath: String,
    val network: String,
    val contractAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val tokenType: String,
    val externalId: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "evm_balances",
    foreignKeys = [
        ForeignKey(
            entity = WalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["walletId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = EVMTokenEntity::class,
            parentColumns = ["id"],
            childColumns = ["tokenId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["walletId", "tokenId"], unique = true),
        Index(value = ["tokenId"])
    ]
)
data class EVMBalanceEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val walletId: String,
    val tokenId: String,
    val externalTokenId: String,
    val address: String,
    val balanceWei: String,
    val balanceDecimal: String,
    val usdValue: Double,
    val updatedAt: Long = System.currentTimeMillis()
)