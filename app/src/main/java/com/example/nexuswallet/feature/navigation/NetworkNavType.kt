package com.example.nexuswallet.feature.navigation

import android.os.Bundle
import androidx.navigation.NavType
import com.example.nexuswallet.feature.wallet.domain.model.Network
import com.example.nexuswallet.feature.wallet.domain.model.BitcoinNetwork
import kotlinx.serialization.json.Json

object NetworkNavType : NavType<Network>(isNullableAllowed = false) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        coerceInputValues = true
    }

    override fun get(bundle: Bundle, key: String): Network? {
        return bundle.getString(key)?.let { jsonString ->
            runCatching {
                json.decodeFromString<Network>(jsonString)
            }.getOrNull()
        }
    }

    override fun parseValue(value: String): Network {
        return runCatching {
            json.decodeFromString<Network>(value)
        }.getOrElse {
            BitcoinNetwork.Testnet
        }
    }

    override fun put(bundle: Bundle, key: String, value: Network) {
        bundle.putString(key, json.encodeToString(value))
    }

    override fun serializeAsValue(value: Network): String {
        return json.encodeToString(value)
    }
}