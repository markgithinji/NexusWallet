package com.example.nexuswallet.feature.wallet.data.local

import androidx.room.TypeConverter
import com.example.nexuswallet.feature.core.domain.model.FeeLevel
import com.example.nexuswallet.feature.ethereum.domain.model.EVMTokenType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinBalance
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.EVMBalance
import com.example.nexuswallet.feature.wallet.domain.model.EVMToken
import com.example.nexuswallet.feature.wallet.domain.model.EthereumNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.SPLToken
import com.example.nexuswallet.feature.wallet.domain.model.SolanaBalance
import com.example.nexuswallet.feature.wallet.domain.model.SolanaNetwork
import com.example.nexuswallet.feature.wallet.domain.model.TransactionStatus
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        coerceInputValues = true
    }

    // ============ NETWORK CONVERTERS ============

    @TypeConverter
    fun fromNetwork(network: Network): String {
        return json.encodeToString(network)
    }

    @TypeConverter
    fun toNetwork(network: String): Network {
        if (network.isBlank()) {
            throw IllegalArgumentException("Empty Network string")
        }
        return try {
            json.decodeFromString<Network>(network)
        } catch (e: Exception) {
            throw e
        }
    }

    // ============ BITCOIN NETWORK ============

    @TypeConverter
    fun fromBitcoinNetwork(network: BitcoinNetwork): String {
        return json.encodeToString(network)
    }

    @TypeConverter
    fun toBitcoinNetwork(network: String): BitcoinNetwork {
        if (network.isBlank()) {
            return BitcoinNetwork.Testnet
        }
        return try {
            json.decodeFromString<BitcoinNetwork>(network)
        } catch (e: Exception) {
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
            return EthereumNetwork.Sepolia
        }
        return try {
            json.decodeFromString<EthereumNetwork>(network)
        } catch (e: Exception) {
            EthereumNetwork.Sepolia
        }
    }

    // ============ SOLANA NETWORK ============

    @TypeConverter
    fun fromSolanaNetwork(network: SolanaNetwork): String {
        return json.encodeToString(network)
    }

    @TypeConverter
    fun toSolanaNetwork(network: String): SolanaNetwork {
        if (network.isBlank()) {
            return SolanaNetwork.Devnet
        }
        return try {
            json.decodeFromString<SolanaNetwork>(network)
        } catch (e: Exception) {
            SolanaNetwork.Devnet
        }
    }

    // ============ TOKEN TYPE ============

    @TypeConverter
    fun fromTokenType(type: EVMTokenType): String {
        return type.name
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
            throw e
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
            FeeLevel.NORMAL
        }
    }

    // ============ LIST CONVERTERS ============

    @TypeConverter
    fun fromBitcoinNetworkMap(map: Map<BitcoinNetwork, BitcoinBalance>): String {
        return json.encodeToString(map)
    }

    @TypeConverter
    fun toBitcoinNetworkMap(map: String): Map<BitcoinNetwork, BitcoinBalance> {
        if (map.isBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<BitcoinNetwork, BitcoinBalance>>(map)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    @TypeConverter
    fun fromSolanaNetworkMap(map: Map<SolanaNetwork, SolanaBalance>): String {
        return json.encodeToString(map)
    }

    @TypeConverter
    fun toSolanaNetworkMap(map: String): Map<SolanaNetwork, SolanaBalance> {
        if (map.isBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<SolanaNetwork, SolanaBalance>>(map)
        } catch (e: Exception) {
            emptyMap()
        }
    }

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
            emptyList()
        }
    }

    @TypeConverter
    fun fromSPLTokenList(tokens: List<SPLToken>): String {
        return json.encodeToString(tokens)
    }

    @TypeConverter
    fun toSPLTokenList(tokens: String): List<SPLToken> {
        if (tokens.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<SPLToken>>(tokens)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ============ GENERIC LIST CONVERTER ============

    @TypeConverter
    fun fromStringList(list: List<String>): String {
        return json.encodeToString(list)
    }

    @TypeConverter
    fun toStringList(list: String): List<String> {
        if (list.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(list)
        } catch (e: Exception) {
            emptyList()
        }
    }
}