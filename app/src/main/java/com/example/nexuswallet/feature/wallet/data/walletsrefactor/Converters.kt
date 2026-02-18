package com.example.nexuswallet.feature.wallet.data.walletsrefactor

import androidx.room.TypeConverter
import com.example.nexuswallet.feature.coin.bitcoin.BitcoinNetwork
import com.example.nexuswallet.feature.coin.usdc.domain.EthereumNetwork
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromBitcoinNetwork(network: BitcoinNetwork): String {
        return network.name
    }

    @TypeConverter
    fun toBitcoinNetwork(network: String): BitcoinNetwork {
        return BitcoinNetwork.valueOf(network)
    }

    @TypeConverter
    fun fromEthereumNetwork(network: EthereumNetwork): String {
        return json.encodeToString(network)
    }

    @TypeConverter
    fun toEthereumNetwork(network: String): EthereumNetwork {
        return json.decodeFromString(network)
    }
}