package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import android.util.Log
import androidx.room.TypeConverter
import com.example.nexuswallet.feature.coin.bitcoin.FeeLevel
import com.example.nexuswallet.feature.wallet.domain.TransactionStatus
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        coerceInputValues = true
    }

    // ============ BITCOIN NETWORK ============

    @TypeConverter
    fun fromBitcoinNetwork(network: BitcoinNetwork): String {
        return network.name
    }

    @TypeConverter
    fun toBitcoinNetwork(network: String): BitcoinNetwork {
        if (network.isBlank()) {
            Log.w("Converters", "Empty BitcoinNetwork value found, defaulting to Testnet")
            return BitcoinNetwork.Testnet
        }

        return try {
            // Handle both enum name and serialized name
            when (network) {
                "BitcoinMainnet", "Mainnet" -> BitcoinNetwork.Mainnet
                "BitcoinTestnet", "Testnet" -> BitcoinNetwork.Testnet
                else -> BitcoinNetwork.Testnet
            }
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Unknown BitcoinNetwork: '$network', defaulting to Testnet")
            BitcoinNetwork.Testnet
        }
    }

    // ============ ETHEREUM NETWORK ============

    @TypeConverter
    fun fromEthereumNetwork(network: EthereumNetwork): String {
        return json.encodeToString(network)
    }

    @TypeConverter
    fun toEthereumNetwork(network: String): EthereumNetwork {
        if (network.isBlank()) {
            Log.w("Converters", "Empty EthereumNetwork value found, defaulting to Sepolia")
            return EthereumNetwork.Sepolia
        }

        return try {
            json.decodeFromString<EthereumNetwork>(network)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to decode EthereumNetwork: $network", e)
            // Fallback to legacy string values
            when {
                network.contains("mainnet", ignoreCase = true) -> EthereumNetwork.Mainnet
                network.contains("sepolia", ignoreCase = true) -> EthereumNetwork.Sepolia
                else -> EthereumNetwork.Sepolia
            }
        }
    }

    // ============ SOLANA NETWORK ============

    @TypeConverter
    fun fromSolanaNetwork(network: SolanaNetwork): String {
        return network.name
    }

    @TypeConverter
    fun toSolanaNetwork(network: String): SolanaNetwork {
        if (network.isBlank()) {
            Log.w("Converters", "Empty SolanaNetwork value found, defaulting to Devnet")
            return SolanaNetwork.Devnet
        }

        return try {
            // Handle both enum name and serialized name
            when (network) {
                "SolanaMainnet", "Mainnet" -> SolanaNetwork.Mainnet
                "SolanaDevnet", "Devnet" -> SolanaNetwork.Devnet
                else -> SolanaNetwork.Devnet
            }
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Unknown SolanaNetwork: '$network', defaulting to Devnet")
            SolanaNetwork.Devnet
        }
    }

    // ============ TOKEN TYPE ============

    @TypeConverter
    fun fromTokenType(type: TokenType): String {
        return type.name
    }

    @TypeConverter
    fun toTokenType(type: String): TokenType {
        if (type.isBlank()) {
            return TokenType.ERC20
        }

        return try {
            TokenType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            Log.e("Converters", "Unknown TokenType: '$type', defaulting to ERC20")
            TokenType.ERC20
        }
    }

    // ============ EVM TOKEN CONVERTER ============

    @TypeConverter
    fun fromEVMToken(token: EVMToken): String {
        return json.encodeToString(token)
    }

    @TypeConverter
    fun toEVMToken(token: String): EVMToken {
        if (token.isBlank()) {
            throw IllegalArgumentException("Empty EVMToken string")
        }

        return try {
            json.decodeFromString<EVMToken>(token)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to decode EVMToken: $token", e)
            throw e // Better to crash than return wrong token
        }
    }

    // ============ TRANSACTION STATUS ============

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
            Log.e("Converters", "Unknown TransactionStatus: '$status', defaulting to PENDING")
            TransactionStatus.PENDING
        }
    }

    // ============ FEE LEVEL ============

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
            Log.e("Converters", "Unknown FeeLevel: '$level', defaulting to NORMAL")
            FeeLevel.NORMAL
        }
    }

    // ============ LIST CONVERTERS ============

    @TypeConverter
    fun fromEVMTokenList(tokens: List<EVMToken>): String {
        return json.encodeToString(tokens)
    }

    @TypeConverter
    fun toEVMTokenList(tokens: String): List<EVMToken> {
        if (tokens.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<EVMToken>>(tokens)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to decode EVMToken list", e)
            emptyList()
        }
    }

    @TypeConverter
    fun fromEVMBalanceList(balances: List<EVMBalance>): String {
        return json.encodeToString(balances)
    }

    @TypeConverter
    fun toEVMBalanceList(balances: String): List<EVMBalance> {
        if (balances.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<EVMBalance>>(balances)
        } catch (e: Exception) {
            Log.e("Converters", "Failed to decode EVMBalance list", e)
            emptyList()
        }
    }
}