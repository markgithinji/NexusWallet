package com.example.nexuswallet.feature.authentication.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Store encrypted mnemonic for a specific wallet
     * @param walletId The wallet identifier
     * @param encryptedMnemonic The encrypted mnemonic string
     * @param iv The initialization vector used for encryption
     */
    suspend fun storeEncryptedMnemonic(
        walletId: String,
        encryptedMnemonic: String,
        iv: ByteArray
    ) {
        // Create dynamic keys for this specific wallet
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        safeEdit {
            dataStore.edit { preferences ->
                preferences[key] = encryptedMnemonic
                preferences[ivKey] = iv.toHex()
            }
        }
    }

    /**
     * Retrieve encrypted mnemonic for a specific wallet
     * @param walletId The wallet identifier
     * @return Pair of encrypted mnemonic and IV, or null if not found
     */
    suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        return safeGet {
            val preferences = dataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                null
            }
        }
    }

    /**
     * Store encrypted private key for a specific wallet and key type
     * @param walletId The wallet identifier
     * @param keyType The type of key (e.g., "BTC", "ETH", "SOL")
     * @param encryptedKey The encrypted private key string
     * @param iv The initialization vector used for encryption
     */
    suspend fun storeEncryptedPrivateKey(
        walletId: String,
        keyType: String,
        encryptedKey: String,
        iv: ByteArray
    ) {
        val key = stringPreferencesKey("${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId}_$keyType")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId}_$keyType")

        safeEdit {
            dataStore.edit { preferences ->
                preferences[key] = encryptedKey
                preferences[ivKey] = iv.toHex()
            }
        }
    }

    /**
     * Retrieve encrypted private key for a specific wallet and key type
     * @param walletId The wallet identifier
     * @param keyType The type of key (e.g., "BTC", "ETH", "SOL")
     * @return Pair of encrypted private key and IV, or null if not found
     */
    suspend fun getEncryptedPrivateKey(
        walletId: String,
        keyType: String
    ): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId}_$keyType")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId}_$keyType")

        return safeGet {
            val preferences = dataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                null
            }
        }
    }

    /**
     * Retrieve encrypted backup for a specific wallet
     * @param walletId The wallet identifier
     * @return Pair of encrypted backup and IV, or null if not found
     */
    suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>? {
        val backupKey = stringPreferencesKey("${ENCRYPTED_BACKUP_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_backup_$walletId")

        return safeGet {
            val preferences = dataStore.data.first()
            val encrypted = preferences[backupKey]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                null
            }
        }
    }

    /**
     * Store hashed PIN for authentication
     * @param pinHash The hashed PIN string
     */
    suspend fun storePinHash(pinHash: String) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[PIN_HASH_KEY] = pinHash
            }
        }
    }

    /**
     * Retrieve stored PIN hash
     * @return The hashed PIN string, or null if not set
     */
    suspend fun getPinHash(): String? {
        return safeGet {
            val preferences = dataStore.data.first()
            preferences[PIN_HASH_KEY]
        }
    }

    /**
     * Enable or disable biometric authentication
     * @param enabled true to enable biometric, false to disable
     */
    suspend fun setBiometricEnabled(enabled: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[BIOMETRIC_ENABLED_KEY] = enabled
            }
        }
    }

    /**
     * Check if biometric authentication is enabled
     * @return true if biometric is enabled, false otherwise
     */
    suspend fun isBiometricEnabled(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[BIOMETRIC_ENABLED_KEY] ?: false
        } ?: false
    }

    /**
     * Clear all sensitive data (for logout/reset)
     */
    suspend fun clearAll() {
        safeEdit {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    /**
     * Clear stored PIN hash (for PIN reset)
     */
    suspend fun clearPinHash() {
        safeEdit {
            dataStore.edit { preferences ->
                preferences.remove(PIN_HASH_KEY)
            }
        }
    }

    suspend fun saveLastAuthenticationTime(timestamp: Long) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[LAST_AUTH_TIME_KEY] = timestamp
            }
        }
    }

    /**
     * Get last authentication timestamp
     * @return timestamp in milliseconds, or null if never authenticated
     */
    suspend fun getLastAuthenticationTime(): Long? {
        return safeGet {
            val preferences = dataStore.data.first()
            preferences[LAST_AUTH_TIME_KEY]
        }
    }

    /**
     * Convert ByteArray to hexadecimal string
     */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    /**
     * Convert hexadecimal string to ByteArray
     */
    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        // Keys for encrypted storage
        private val ENCRYPTED_MNEMONIC_KEY = stringPreferencesKey("encrypted_mnemonic")
        private val ENCRYPTED_PRIVATE_KEY_KEY = stringPreferencesKey("encrypted_private_key")
        private val ENCRYPTED_BACKUP_KEY = stringPreferencesKey("encrypted_backup")
        private val INITIALIZATION_VECTOR_KEY = stringPreferencesKey("initialization_vector")

        // For biometric/PIN authentication
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val LAST_AUTH_TIME_KEY = longPreferencesKey("last_authentication_time")
    }
}