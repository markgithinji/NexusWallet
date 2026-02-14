package com.example.nexuswallet.feature.coin.solana


import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "SolanaTransaction")
data class SolanaTransactionEntity(
    @PrimaryKey
    val id: String,
    val walletId: String,
    val fromAddress: String,
    val toAddress: String,
    val status: String,
    val timestamp: Long,
    val note: String?,
    val feeLevel: String,
    val amountLamports: Long,
    val amountSol: String,
    val feeLamports: Long,
    val feeSol: String,
    val blockhash: String,
    val signedData: ByteArray?,
    val signature: ByteArray?,
    val network: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SolanaTransactionEntity

        if (id != other.id) return false
        if (walletId != other.walletId) return false
        if (fromAddress != other.fromAddress) return false
        if (toAddress != other.toAddress) return false
        if (status != other.status) return false
        if (timestamp != other.timestamp) return false
        if (note != other.note) return false
        if (feeLevel != other.feeLevel) return false
        if (amountLamports != other.amountLamports) return false
        if (amountSol != other.amountSol) return false
        if (feeLamports != other.feeLamports) return false
        if (feeSol != other.feeSol) return false
        if (blockhash != other.blockhash) return false
        if (network != other.network) return false
        if (!signedData.contentEquals(other.signedData)) return false
        if (!signature.contentEquals(other.signature)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + walletId.hashCode()
        result = 31 * result + fromAddress.hashCode()
        result = 31 * result + toAddress.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (note?.hashCode() ?: 0)
        result = 31 * result + feeLevel.hashCode()
        result = 31 * result + amountLamports.hashCode()
        result = 31 * result + amountSol.hashCode()
        result = 31 * result + feeLamports.hashCode()
        result = 31 * result + feeSol.hashCode()
        result = 31 * result + blockhash.hashCode()
        result = 31 * result + network.hashCode()
        result = 31 * result + (signedData?.contentHashCode() ?: 0)
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }
}