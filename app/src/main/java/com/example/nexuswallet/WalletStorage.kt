package com.example.nexuswallet

import android.content.Context
import android.content.SharedPreferences
import com.example.nexuswallet.data.model.BitcoinWallet
import com.example.nexuswallet.data.model.CryptoWallet
import com.example.nexuswallet.data.model.EthereumWallet
import com.example.nexuswallet.data.model.MultiChainWallet
import com.example.nexuswallet.data.model.SolanaWallet
import com.example.nexuswallet.data.model.Transaction
import com.example.nexuswallet.data.model.WalletBalance
import com.example.nexuswallet.data.model.WalletSettings
import kotlinx.serialization.json.Json

class WalletStorage(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nexus_wallet_v2", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveWallet(wallet: CryptoWallet) {
        val key = "wallet_${wallet.id}"
        val jsonStr = when (wallet) {
            is BitcoinWallet -> json.encodeToString(wallet)
            is EthereumWallet -> json.encodeToString(wallet)
            is MultiChainWallet -> json.encodeToString(wallet)
            is SolanaWallet -> json.encodeToString(wallet)
            else -> throw IllegalArgumentException("Unknown wallet type")
        }
        prefs.edit().putString(key, jsonStr).apply()
    }

    fun loadWallet(walletId: String): CryptoWallet? {
        val jsonStr = prefs.getString("wallet_$walletId", null) ?: return null

        return try {
            json.decodeFromString<BitcoinWallet>(jsonStr)
        } catch (e: Exception) {
            try {
                json.decodeFromString<EthereumWallet>(jsonStr)
            } catch (e: Exception) {
                try {
                    json.decodeFromString<MultiChainWallet>(jsonStr)
                } catch (e: Exception) {
                    try {
                        json.decodeFromString<SolanaWallet>(jsonStr)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
    }

    fun loadAllWallets(): List<CryptoWallet> {
        return prefs.all.keys
            .filter { it.startsWith("wallet_") }
            .mapNotNull { loadWallet(it.removePrefix("wallet_")) }
    }

    fun saveEncryptedMnemonic(walletId: String, encryptedData: String) {
        prefs.edit().putString("mnemonic_$walletId", encryptedData).apply()
    }

    fun loadEncryptedMnemonic(walletId: String): String? {
        return prefs.getString("mnemonic_$walletId", null)
    }

    fun saveWalletBalance(balance: WalletBalance) {
        val jsonStr = json.encodeToString(balance)
        prefs.edit().putString("balance_${balance.walletId}", jsonStr).apply()
    }

    fun loadWalletBalance(walletId: String): WalletBalance? {
        val jsonStr = prefs.getString("balance_$walletId", null) ?: return null
        return try {
            json.decodeFromString<WalletBalance>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    fun saveTransactions(walletId: String, transactions: List<Transaction>) {
        val jsonStr = json.encodeToString(transactions)
        prefs.edit().putString("tx_$walletId", jsonStr).apply()
    }

    fun loadTransactions(walletId: String): List<Transaction> {
        val jsonStr = prefs.getString("tx_$walletId", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<Transaction>>(jsonStr)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveSettings(settings: WalletSettings) {
        val jsonStr = json.encodeToString(settings)
        prefs.edit().putString("settings_${settings.walletId}", jsonStr).apply()
    }

    fun loadSettings(walletId: String): WalletSettings? {
        val jsonStr = prefs.getString("settings_$walletId", null) ?: return null
        return try {
            json.decodeFromString<WalletSettings>(jsonStr)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteWallet(walletId: String) {
        prefs.edit()
            .remove("wallet_$walletId")
            .remove("mnemonic_$walletId")
            .remove("balance_$walletId")
            .remove("tx_$walletId")
            .remove("settings_$walletId")
            .apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun walletExists(walletId: String): Boolean {
        return prefs.contains("wallet_$walletId")
    }

    fun getAllWalletIds(): List<String> {
        return prefs.all.keys
            .filter { it.startsWith("wallet_") }
            .map { it.removePrefix("wallet_") }
    }
}