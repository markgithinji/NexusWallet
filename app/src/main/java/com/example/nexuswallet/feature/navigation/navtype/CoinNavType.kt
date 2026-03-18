package com.example.nexuswallet.feature.navigation.navtype

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinCoin
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import com.example.nexuswallet.feature.wallet.domain.model.Coin
import kotlinx.serialization.json.Json

object CoinNavType : NavType<Coin>(isNullableAllowed = false) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        coerceInputValues = true
    }

    override fun get(bundle: Bundle, key: String): Coin? {
        return bundle.getString(key)?.let { jsonString ->
            runCatching {
                json.decodeFromString<Coin>(jsonString)
            }.getOrNull()
        }
    }

    override fun parseValue(value: String): Coin {
        val decodedValue = Uri.decode(value)

        return runCatching {
            json.decodeFromString<Coin>(decodedValue)
        }.getOrElse { error ->
            BitcoinCoin(
                address = "",
                publicKey = "",
                network = BitcoinNetwork.Mainnet,
                xpub = ""
            )
        }
    }

    override fun put(bundle: Bundle, key: String, value: Coin) {
        bundle.putString(key, json.encodeToString(value))
    }

    override fun serializeAsValue(value: Coin): String {
        return Uri.encode(json.encodeToString(value))
    }
}