package com.example.nexuswallet

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nexuswallet.data.model.WalletBackup
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.encryptedDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "encrypted_wallet_data"
)

/**
 * Secure storage using Encrypted DataStore for sensitive data
 * This handles mnemonic phrases, private keys, and other sensitive information
 */
class SecureStorage(private val context: Context) {

    private val json = Json { encodeDefaults = true }

    companion object {
        // Keys for encrypted storage
        private val ENCRYPTED_MNEMONIC_KEY = stringPreferencesKey("encrypted_mnemonic")
        private val ENCRYPTED_PRIVATE_KEY_KEY = stringPreferencesKey("encrypted_private_key")
        private val ENCRYPTED_SEED_KEY = stringPreferencesKey("encrypted_seed")
        private val ENCRYPTED_BACKUP_KEY = stringPreferencesKey("encrypted_backup")
        private val KEY_MATERIAL_ENCRYPTED_KEY = stringPreferencesKey("key_material_encrypted")
        private val INITIALIZATION_VECTOR_KEY = stringPreferencesKey("initialization_vector")
        private val SALT_KEY = stringPreferencesKey("salt")

        // For biometric/PIN authentication
        private val BIOMETRIC_ENABLED_KEY = stringPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val LOCK_TIMEOUT_KEY = stringPreferencesKey("lock_timeout")
    }

    /**
     * Store encrypted mnemonic phrase
     */
    suspend fun storeEncryptedMnemonic(
        walletId: String,
        encryptedMnemonic: String,
        iv: ByteArray
    ) {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        context.encryptedDataStore.edit { preferences ->
            preferences[key] = encryptedMnemonic
            preferences[ivKey] = iv.toHex()
        }
    }

    /**
     * Retrieve encrypted mnemonic
     */
    suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        return try {
            val preferences = context.encryptedDataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Store encrypted private key
     */
    suspend fun storeEncryptedPrivateKey(
        walletId: String,
        keyType: String, // "BITCOIN", "ETHEREUM", etc.
        encryptedKey: String,
        iv: ByteArray
    ) {
        val key = stringPreferencesKey("${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId}_$keyType")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId}_$keyType")

        context.encryptedDataStore.edit { preferences ->
            preferences[key] = encryptedKey
            preferences[ivKey] = iv.toHex()
        }
    }

    /**
     * Get encrypted private key
     */
    suspend fun getEncryptedPrivateKey(
        walletId: String,
        keyType: String
    ): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId}_$keyType")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId}_$keyType")

        return try {
            val preferences = context.encryptedDataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Store encrypted backup
     */
    suspend fun storeEncryptedBackup(
        walletId: String,
        backupData: WalletBackup,
        encryptedData: String,
        iv: ByteArray
    ) {
        val backupKey = stringPreferencesKey("${ENCRYPTED_BACKUP_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_backup_$walletId")

        context.encryptedDataStore.edit { preferences ->
            preferences[backupKey] = encryptedData
            preferences[ivKey] = iv.toHex()
        }

        // Store backup metadata in regular storage
        storeBackupMetadata(walletId, backupData)
    }

    /**
     * Get encrypted backup
     */
    suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>? {
        val backupKey = stringPreferencesKey("${ENCRYPTED_BACKUP_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_backup_$walletId")

        return try {
            val preferences = context.encryptedDataStore.data.first()
            val encrypted = preferences[backupKey]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Store PIN hash for authentication
     */
    suspend fun storePinHash(pinHash: String) {
        context.encryptedDataStore.edit { preferences ->
            preferences[PIN_HASH_KEY] = pinHash
        }
    }

    /**
     * Get stored PIN hash
     */
    suspend fun getPinHash(): String? {
        return try {
            val preferences = context.encryptedDataStore.data.first()
            preferences[PIN_HASH_KEY]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Set biometric enabled status
     */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.encryptedDataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled.toString()
        }
    }

    /**
     * Check if biometric is enabled
     */
    suspend fun isBiometricEnabled(): Boolean {
        return try {
            val preferences = context.encryptedDataStore.data.first()
            preferences[BIOMETRIC_ENABLED_KEY]?.toBoolean() ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear all encrypted data for a wallet (on deletion)
     */
//    suspend fun clearWalletData(walletId: String) {
//        context.encryptedDataStore.edit { preferences ->
//            // Remove all keys related to this wallet
//            preferences.keys.forEach { key ->
//                if (key.name.contains(walletId)) {
//                    preferences.remove(key)
//                }
//            }
//        }
//    }

    /**
     * Clear all sensitive data (for logout)
     */
    suspend fun clearAll() {
        context.encryptedDataStore.edit { preferences ->
            preferences.clear()
        }
    }

    /**
     * Check if wallet has encrypted data
     */
    suspend fun hasEncryptedData(walletId: String): Boolean {
        return getEncryptedMnemonic(walletId) != null
    }

    // Helper methods for storing backup metadata in regular storage
    private suspend fun storeBackupMetadata(walletId: String, backupData: WalletBackup) {
        val storage = WalletStorage(context)
        storage.saveBackupMetadata(backupData)
    }

    // Utility functions
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}