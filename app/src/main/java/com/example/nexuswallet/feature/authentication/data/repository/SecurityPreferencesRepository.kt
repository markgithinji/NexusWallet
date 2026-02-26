package com.example.nexuswallet.feature.authentication.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SecurityPreferencesRepository {

    override suspend fun storeEncryptedMnemonic(
        walletId: String,
        encryptedMnemonic: String,
        iv: ByteArray
    ) {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_$walletId")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_$walletId")

        safeEdit {
            dataStore.edit { preferences ->
                preferences[key] = encryptedMnemonic
                preferences[ivKey] = iv.toHex()
            }
        }
    }

    override suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>? {
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

    override suspend fun storeEncryptedPrivateKey(
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

    override suspend fun getEncryptedPrivateKey(
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

    override suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>? {
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

    override suspend fun storePinHash(pinHash: String) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[PIN_HASH_KEY] = pinHash
            }
        }
    }

    override suspend fun getPinHash(): String? {
        return safeGet {
            val preferences = dataStore.data.first()
            preferences[PIN_HASH_KEY]
        }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[BIOMETRIC_ENABLED_KEY] = enabled
            }
        }
    }

    override suspend fun isBiometricEnabled(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[BIOMETRIC_ENABLED_KEY] ?: false
        } ?: false
    }

    override suspend fun clearAll() {
        safeEdit {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    override suspend fun clearPinHash() {
        safeEdit {
            dataStore.edit { preferences ->
                preferences.remove(PIN_HASH_KEY)
            }
        }
    }

    override suspend fun saveLastAuthenticationTime(timestamp: Long) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[LAST_AUTH_TIME_KEY] = timestamp
            }
        }
    }

    override suspend fun getLastAuthenticationTime(): Long? {
        return safeGet {
            val preferences = dataStore.data.first()
            preferences[LAST_AUTH_TIME_KEY]
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private val ENCRYPTED_MNEMONIC_KEY = stringPreferencesKey("encrypted_mnemonic")
        private val ENCRYPTED_PRIVATE_KEY_KEY = stringPreferencesKey("encrypted_private_key")
        private val ENCRYPTED_BACKUP_KEY = stringPreferencesKey("encrypted_backup")
        private val INITIALIZATION_VECTOR_KEY = stringPreferencesKey("initialization_vector")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val LAST_AUTH_TIME_KEY = longPreferencesKey("last_authentication_time")
    }
}