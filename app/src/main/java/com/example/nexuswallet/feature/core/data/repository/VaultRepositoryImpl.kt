package com.example.nexuswallet.feature.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nexuswallet.feature.core.data.util.safeEdit
import com.example.nexuswallet.feature.core.data.util.safeGet
import com.example.nexuswallet.feature.core.domain.repository.VaultRepository
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaultRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : VaultRepository {

    override suspend fun storeEncryptedMnemonic(
        walletId: String,
        encryptedMnemonic: String,
        iv: ByteArray
    ) {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_${walletId.lowercase()}")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId.lowercase()}")

        safeEdit {
            dataStore.edit { preferences ->
                preferences[key] = encryptedMnemonic
                preferences[ivKey] = iv.toHex()
            }
        }
    }

    override suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>? {
        val key = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_${walletId.lowercase()}")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId.lowercase()}")

        return safeGet {
            val preferences = dataStore.data.first()
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if ((encrypted != null) && (ivHex != null)) {
                Pair(encrypted, ivHex.decodeHex())
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
        val keyName = "${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId.lowercase()}_${keyType.lowercase()}"
        val ivKeyName = "${INITIALIZATION_VECTOR_KEY.name}_${walletId.lowercase()}_${keyType.lowercase()}"
        
        val key = stringPreferencesKey(keyName)
        val ivKey = stringPreferencesKey(ivKeyName)

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
        val keyName = "${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId.lowercase()}_${keyType.lowercase()}"
        val ivKeyName = "${INITIALIZATION_VECTOR_KEY.name}_${walletId.lowercase()}_${keyType.lowercase()}"
        
        return safeGet {
            val preferences = dataStore.data.first()
            val key = stringPreferencesKey(keyName)
            val ivKey = stringPreferencesKey(ivKeyName)
            val encrypted = preferences[key]
            val ivHex = preferences[ivKey]

            if ((encrypted != null) && (ivHex != null)) {
                Pair(encrypted, ivHex.decodeHex())
            } else {
                null
            }
        }
    }

    override suspend fun storeSecurityBundle(
        walletId: String,
        mnemonic: Pair<String, ByteArray>,
        privateKeys: Map<String, Pair<String, ByteArray>>
    ) {
        safeEdit {
            dataStore.edit { preferences ->
                // Store Mnemonic
                val mKey = stringPreferencesKey("${ENCRYPTED_MNEMONIC_KEY.name}_${walletId.lowercase()}")
                val mIvKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_${walletId.lowercase()}")
                preferences[mKey] = mnemonic.first
                preferences[mIvKey] = mnemonic.second.toHex()

                // Store all Private Keys
                privateKeys.forEach { (keyType, data) ->
                    val pkName = "${ENCRYPTED_PRIVATE_KEY_KEY.name}_${walletId.lowercase()}_${keyType.lowercase()}"
                    val pkIvName = "${INITIALIZATION_VECTOR_KEY.name}_${walletId.lowercase()}_${keyType.lowercase()}"
                    preferences[stringPreferencesKey(pkName)] = data.first
                    preferences[stringPreferencesKey(pkIvName)] = data.second.toHex()
                }
            }
        }
    }

    override suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>? {
        val backupKey = stringPreferencesKey("${ENCRYPTED_BACKUP_KEY.name}_${walletId.lowercase()}")
        val ivKey = stringPreferencesKey("${INITIALIZATION_VECTOR_KEY.name}_backup_${walletId.lowercase()}")

        return safeGet {
            val preferences = dataStore.data.first()
            val encrypted = preferences[backupKey]
            val ivHex = preferences[ivKey]

            if ((encrypted != null) && (ivHex != null)) {
                Pair(encrypted, ivHex.decodeHex())
            } else {
                null
            }
        }
    }

    override suspend fun clearVault() {
        safeEdit {
            dataStore.edit { preferences ->
                // Clear all vault related keys
                val keysToRemove = preferences.asMap().keys.filter { 
                    it.name.contains("encrypted_mnemonic") || 
                    it.name.contains("encrypted_private_key") || 
                    it.name.contains("encrypted_backup") ||
                    it.name.contains("initialization_vector")
                }
                keysToRemove.forEach { preferences.remove(it) }
            }
        }
    }

    companion object {
        private val ENCRYPTED_MNEMONIC_KEY = stringPreferencesKey("encrypted_mnemonic")
        private val ENCRYPTED_PRIVATE_KEY_KEY = stringPreferencesKey("encrypted_private_key")
        private val ENCRYPTED_BACKUP_KEY = stringPreferencesKey("encrypted_backup")
        private val INITIALIZATION_VECTOR_KEY = stringPreferencesKey("initialization_vector")
    }
}
