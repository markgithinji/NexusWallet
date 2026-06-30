package com.example.nexuswallet.feature.authentication.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nexuswallet.feature.authentication.data.util.safeEdit
import com.example.nexuswallet.feature.authentication.data.util.safeGet
import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    override suspend fun setPrivacyModeEnabled(enabled: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[PRIVACY_MODE_ENABLED_KEY] = enabled
            }
        }
    }

    override suspend fun isPrivacyModeEnabled(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[PRIVACY_MODE_ENABLED_KEY] ?: false
        } ?: false
    }

    override suspend fun setRequireAuthForSend(enabled: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[REQUIRE_AUTH_FOR_SEND_KEY] = enabled
            }
        }
    }

    override suspend fun isRequireAuthForSend(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[REQUIRE_AUTH_FOR_SEND_KEY] ?: false
        } ?: false
    }

    override suspend fun setSelectedCurrency(currencyCode: String) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[SELECTED_CURRENCY_KEY] = currencyCode
            }
        }
    }

    override suspend fun getSelectedCurrency(): String {
        return safeGet(defaultValue = "USD") {
            val preferences = dataStore.data.first()
            preferences[SELECTED_CURRENCY_KEY] ?: "USD"
        } ?: "USD"
    }

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[THEME_MODE_KEY] = themeMode.name
            }
        }
    }

    override suspend fun getThemeMode(): ThemeMode {
        return safeGet(defaultValue = ThemeMode.SYSTEM) {
            val preferences = dataStore.data.first()
            val themeModeName = preferences[THEME_MODE_KEY]
            if (themeModeName != null) {
                try {
                    ThemeMode.valueOf(themeModeName.uppercase())
                } catch (e: IllegalArgumentException) {
                    ThemeMode.SYSTEM
                }
            } else {
                ThemeMode.SYSTEM
            }
        } ?: ThemeMode.SYSTEM
    }

    override fun observePinHash(): Flow<String?> =
        dataStore.data.map { preferences ->
            preferences[PIN_HASH_KEY]
        }

    override fun observeBiometricEnabled(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[BIOMETRIC_ENABLED_KEY] ?: false
        }

    override fun observePrivacyModeEnabled(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PRIVACY_MODE_ENABLED_KEY] ?: false
        }

    override fun observeRequireAuthForSend(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[REQUIRE_AUTH_FOR_SEND_KEY] ?: false
        }

    override fun observeSelectedCurrency(): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SELECTED_CURRENCY_KEY] ?: "USD"
        }

    override fun observeThemeMode(): Flow<ThemeMode> =
        dataStore.data.map { preferences ->
            val themeModeName = preferences[THEME_MODE_KEY]
            if (themeModeName != null) {
                try {
                    ThemeMode.valueOf(themeModeName.uppercase())
                } catch (e: IllegalArgumentException) {
                    ThemeMode.SYSTEM
                }
            } else {
                ThemeMode.SYSTEM
            }
        }

    companion object {
        private val ENCRYPTED_MNEMONIC_KEY = stringPreferencesKey("encrypted_mnemonic")
        private val ENCRYPTED_PRIVATE_KEY_KEY = stringPreferencesKey("encrypted_private_key")
        private val ENCRYPTED_BACKUP_KEY = stringPreferencesKey("encrypted_backup")
        private val INITIALIZATION_VECTOR_KEY = stringPreferencesKey("initialization_vector")
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val LAST_AUTH_TIME_KEY = longPreferencesKey("last_authentication_time")
        private val PRIVACY_MODE_ENABLED_KEY = booleanPreferencesKey("privacy_mode_enabled")
        private val REQUIRE_AUTH_FOR_SEND_KEY = booleanPreferencesKey("require_auth_for_send")
        private val SELECTED_CURRENCY_KEY = stringPreferencesKey("selected_currency")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}