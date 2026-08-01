package com.example.nexuswallet.feature.navigation.navtype

import android.os.Bundle
import androidx.navigation.NavType
import com.example.nexuswallet.feature.navigation.AuthTarget
import kotlinx.serialization.json.Json

object AuthTargetNavType : NavType<AuthTarget>(isNullableAllowed = false) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
        coerceInputValues = true
    }

    override fun get(bundle: Bundle, key: String): AuthTarget? {
        return bundle.getString(key)?.let { jsonString ->
            runCatching {
                json.decodeFromString<AuthTarget>(jsonString)
            }.getOrNull()
        }
    }

    override fun parseValue(value: String): AuthTarget {
        return runCatching {
            json.decodeFromString<AuthTarget>(value)
        }.getOrElse {
            // Return a safe default, though ideally this shouldn't happen
            AuthTarget.WalletDetail("")
        }
    }

    override fun put(bundle: Bundle, key: String, value: AuthTarget) {
        bundle.putString(key, json.encodeToString(value))
    }

    override fun serializeAsValue(value: AuthTarget): String {
        return json.encodeToString(value)
    }
}