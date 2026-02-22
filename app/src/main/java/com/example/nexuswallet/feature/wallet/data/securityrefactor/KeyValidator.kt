package com.example.nexuswallet.feature.wallet.data.securityrefactor

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeyValidator @Inject constructor() {

    fun isValidEthereumPrivateKey(privateKey: String): Boolean {
        return try {
            val key = privateKey.removePrefix("0x")
            key.length == 64 && key.matches(Regex("^[0-9a-fA-F]+$"))
        } catch (e: Exception) {
            false
        }
    }

    fun isValidBitcoinPrivateKey(privateKey: String): Boolean {
        return try {
            when {
                privateKey.startsWith("5") && privateKey.length in 51..52 -> true
                privateKey.startsWith("L") || privateKey.startsWith("K") -> true
                privateKey.startsWith("9") || privateKey.startsWith("c") -> true
                privateKey.length == 64 && privateKey.matches(Regex("^[0-9a-fA-F]+$")) -> true
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isValidSolanaPrivateKey(privateKey: String): Boolean {
        return try {
            val key = privateKey.removePrefix("0x")
            when (key.length) {
                128, 64 -> key.matches(Regex("^[0-9a-fA-F]+$"))
                else -> false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun validatePrivateKey(privateKey: String, keyType: String): Boolean {
        return when (keyType) {
            "ETH_PRIVATE_KEY" -> isValidEthereumPrivateKey(privateKey)
            "BTC_PRIVATE_KEY" -> isValidBitcoinPrivateKey(privateKey)
            "SOLANA_PRIVATE_KEY" -> isValidSolanaPrivateKey(privateKey)
            else -> true
        }
    }

    fun clearKeyFromMemory(key: String) {
        try {
            val chars = key.toCharArray()
            chars.fill('0')
        } catch (e: Exception) {
            // Silently handle failure to clear
        }
    }
}