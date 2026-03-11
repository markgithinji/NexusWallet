package com.example.nexuswallet.feature.wallet.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

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