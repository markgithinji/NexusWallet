package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import androidx.room.TypeConverter
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Bitcoin Network Converter
    @TypeConverter
    fun fromBitcoinNetwork(network: BitcoinNetwork): String {
        return network.name
    }

    @TypeConverter
    fun toBitcoinNetwork(network: String): BitcoinNetwork {
        // Handle empty or blank strings
        if (network.isBlank()) {
            android.util.Log.w("Converters", "Empty BitcoinNetwork value found, defaulting to TESTNET")
            return BitcoinNetwork.TESTNET
        }

        return try {
            BitcoinNetwork.valueOf(network)
        } catch (e: IllegalArgumentException) {
            android.util.Log.e("Converters", "Unknown BitcoinNetwork: '$network', defaulting to TESTNET")
            BitcoinNetwork.TESTNET
        }
    }

    // Ethereum Network Converter
    @TypeConverter
    fun fromEthereumNetwork(network: EthereumNetwork): String {
        return json.encodeToString(network)
    }

    @TypeConverter
    fun toEthereumNetwork(network: String): EthereumNetwork {
        if (network.isBlank()) {
            android.util.Log.w("Converters", "Empty EthereumNetwork value found, defaulting to Sepolia")
            return EthereumNetwork.Sepolia
        }

        return try {
            json.decodeFromString(network)
        } catch (e: Exception) {
            android.util.Log.e("Converters", "Failed to decode EthereumNetwork: $network", e)
            when {
                network.contains("mainnet", ignoreCase = true) -> EthereumNetwork.Mainnet
                network.contains("sepolia", ignoreCase = true) -> EthereumNetwork.Sepolia
                else -> EthereumNetwork.Sepolia
            }
        }
    }

    // Transaction Status Converter
    @TypeConverter
    fun fromTransactionStatus(status: TransactionStatus): String {
        return status.name
    }

    @TypeConverter
    fun toTransactionStatus(status: String): TransactionStatus {
        if (status.isBlank()) {
            return TransactionStatus.PENDING
        }

        return try {
            TransactionStatus.valueOf(status)
        } catch (e: IllegalArgumentException) {
            TransactionStatus.PENDING
        }
    }

    // Fee Level Converter
    @TypeConverter
    fun fromFeeLevel(level: FeeLevel): String {
        return level.name
    }

    @TypeConverter
    fun toFeeLevel(level: String): FeeLevel {
        if (level.isBlank()) {
            return FeeLevel.NORMAL
        }

        return try {
            FeeLevel.valueOf(level)
        } catch (e: IllegalArgumentException) {
            FeeLevel.NORMAL
        }
    }
}