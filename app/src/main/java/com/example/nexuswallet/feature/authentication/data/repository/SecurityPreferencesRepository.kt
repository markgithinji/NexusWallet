package com.example.nexuswallet.feature.authentication.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nexuswallet.feature.wallet.domain.WalletBackup
import com.example.nexuswallet.feature.wallet.data.local.WalletLocalDataSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "secure_storage"
)

@Singleton
class SecurityPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun storeEncryptedMnemonic(
        walletId: String,
        encryptedMnemonic: String,
        iv: ByteArray
    ) {
        // Create dynamic keys for this specific wallet
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        context.dataStore.edit { preferences ->
            preferences[key] = encryptedMnemonic
            preferences[ivKey] = iv.toHex()
        }

        Log.d(" SECURE_STORAGE", "Stored encrypted mnemonic for wallet: $walletId")
    }

    suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        return try {
            val preferences = context.dataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Log.d(" SECURE_STORAGE", "Retrieved encrypted mnemonic for wallet: $walletId")
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                Log.d(" SECURE_STORAGE", "No encrypted mnemonic found for wallet: $walletId")
                null
            }
        } catch (e: Exception) {
            Log.e(" SECURE_STORAGE", "Error getting encrypted mnemonic", e)
            null
        }
    }

    suspend fun storeEncryptedPrivateKey(
        walletId: String,
        keyType: String, // "BITCOIN", "ETHEREUM", etc.
        encryptedKey: String,
        iv: ByteArray
    ) {
        val key = stringPreferencesKey("${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId}_$keyType")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId}_$keyType")

        context.dataStore.edit { preferences ->
            preferences[key] = encryptedKey
            preferences[ivKey] = iv.toHex()
        }

        Log.d(
            " SECURE_STORAGE",
            "Stored encrypted private key for wallet: $walletId, type: $keyType"
        )
    }

    suspend fun getEncryptedPrivateKey(
        walletId: String,
        keyType: String
    ): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId}_$keyType")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId}_$keyType")

        return try {
            val preferences = context.dataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Log.d(
                    " SECURE_STORAGE",
                    "Retrieved encrypted private key for wallet: $walletId, type: $keyType"
                )
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                Log.d(
                    " SECURE_STORAGE",
                    "No encrypted private key found for wallet: $walletId, type: $keyType"
                )
                null
            }
        } catch (e: Exception) {
            Log.e(" SECURE_STORAGE", "Error getting encrypted private key", e)
            null
        }
    }

    suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>? {
        val backupKey = stringPreferencesKey("${ENCRYPTED_BACKUP_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_backup_$walletId")

        return try {
            val preferences = context.dataStore.data.first()
            val encrypted = preferences[backupKey]
            val ivHex = preferences[ivKey]

            if (encrypted != null && ivHex != null) {
                Log.d(" SECURE_STORAGE", "Retrieved encrypted backup for wallet: $walletId")
                Pair(encrypted, hexToBytes(ivHex))
            } else {
                Log.d(" SECURE_STORAGE", "No encrypted backup found for wallet: $walletId")
                null
            }
        } catch (e: Exception) {
            Log.e(" SECURE_STORAGE", "Error getting encrypted backup", e)
            null
        }
    }

    suspend fun storePinHash(pinHash: String) {
        context.dataStore.edit { preferences ->
            preferences[PIN_HASH_KEY] = pinHash
        }
        Log.d(" SECURE_STORAGE", " Stored PIN hash: ${pinHash.take(10)}...")
    }

    suspend fun getPinHash(): String? {
        return try {
            val preferences = context.dataStore.data.first()
            val hash = preferences[PIN_HASH_KEY]
            if (hash != null) {
                Log.d(" SECURE_STORAGE", " Retrieved PIN hash: ${hash.take(10)}...")
            } else {
                Log.d(" SECURE_STORAGE", " No PIN hash found in storage")
            }
            hash
        } catch (e: Exception) {
            Log.e(" SECURE_STORAGE", " Error getting PIN hash", e)
            null
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] = enabled
        }
        Log.d(" SECURE_STORAGE", " Set biometric enabled: $enabled")
    }

    suspend fun isBiometricEnabled(): Boolean {
        return try {
            val preferences = context.dataStore.data.first()
            val enabled = preferences[BIOMETRIC_ENABLED_KEY] ?: false
            Log.d("SECURE_STORAGE", " Retrieved biometric enabled: $enabled")
            enabled
        } catch (e: Exception) {
            Log.e(" SECURE_STORAGE", " Error getting biometric status", e)
            false
        }
    }

    /**
     * Clear all sensitive data (for logout)
     */
    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        Log.d(" SECURE_STORAGE", " Cleared all secure storage")
    }

    suspend fun saveSessionTimeout(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[SESSION_TIMEOUT_KEY] = seconds
        }
        Log.d(" SECURE_STORAGE", " Saved session timeout: ${seconds}s")
    }

    suspend fun getSessionTimeout(): Int {
        return try {
            val preferences = context.dataStore.data.first()
            val timeout = preferences[SESSION_TIMEOUT_KEY] ?: 300 // Default 5 minutes
            Log.d(" SECURE_STORAGE", " Retrieved session timeout: ${timeout}s")
            timeout
        } catch (e: Exception) {
            Log.e(" SECURE_STORAGE", " Error getting session timeout", e)
            300
        }
    }

    suspend fun clearPinHash() {
        context.dataStore.edit { preferences ->
            preferences.remove(PIN_HASH_KEY)
        }
        Log.d(" SECURE_STORAGE", " Cleared PIN hash")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

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
        private val SESSION_TIMEOUT_KEY =
            intPreferencesKey("session_timeout")
    }

}