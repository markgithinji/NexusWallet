package com.example.nexuswallet.feature.settings.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.nexuswallet.feature.core.data.util.safeEdit
import com.example.nexuswallet.feature.core.data.util.safeGet
import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : SettingsRepository {

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

    override suspend fun setSelectedCurrency(currency: SupportedCurrency) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[SELECTED_CURRENCY_KEY] = currency.code
            }
        }
    }

    override suspend fun getSelectedCurrency(): SupportedCurrency {
        return safeGet(defaultValue = SupportedCurrency.USD) {
            val preferences = dataStore.data.first()
            val code = preferences[SELECTED_CURRENCY_KEY] ?: "USD"
            SupportedCurrency.fromCode(code)
        } ?: SupportedCurrency.USD
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

    override fun observeSelectedCurrency(): Flow<SupportedCurrency> =
        dataStore.data.map { preferences ->
            val code = preferences[SELECTED_CURRENCY_KEY] ?: "USD"
            SupportedCurrency.fromCode(code)
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

    override suspend fun setNotificationsEnabled(enabled: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
            }
        }
    }

    override suspend fun isNotificationsEnabled(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[NOTIFICATIONS_ENABLED_KEY] ?: false
        } ?: false
    }

    override fun observeNotificationsEnabled(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] ?: false
        }

    override suspend fun setNotificationRationaleSilenced(silenced: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[NOTIFICATION_RATIONALE_SILENCED_KEY] = silenced
            }
        }
    }

    override suspend fun isNotificationRationaleSilenced(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[NOTIFICATION_RATIONALE_SILENCED_KEY] ?: false
        } ?: false
    }

    override fun observeNotificationRationaleSilenced(): Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[NOTIFICATION_RATIONALE_SILENCED_KEY] ?: false
        }

    override suspend fun setHasRequestedNotificationPermission(requested: Boolean) {
        safeEdit {
            dataStore.edit { preferences ->
                preferences[HAS_REQUESTED_NOTIFICATION_PERMISSION_KEY] = requested
            }
        }
    }

    override suspend fun hasRequestedNotificationPermission(): Boolean {
        return safeGet(defaultValue = false) {
            val preferences = dataStore.data.first()
            preferences[HAS_REQUESTED_NOTIFICATION_PERMISSION_KEY] ?: false
        } ?: false
    }

    override suspend fun clearAll() {
        safeEdit {
            dataStore.edit { preferences ->
                preferences.clear()
            }
        }
    }

    companion object {
        private val BIOMETRIC_ENABLED_KEY = booleanPreferencesKey("biometric_enabled")
        private val PIN_HASH_KEY = stringPreferencesKey("pin_hash")
        private val LAST_AUTH_TIME_KEY = longPreferencesKey("last_authentication_time")
        private val PRIVACY_MODE_ENABLED_KEY = booleanPreferencesKey("privacy_mode_enabled")
        private val REQUIRE_AUTH_FOR_SEND_KEY = booleanPreferencesKey("require_auth_for_send")
        private val SELECTED_CURRENCY_KEY = stringPreferencesKey("selected_currency")
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val NOTIFICATION_RATIONALE_SILENCED_KEY =
            booleanPreferencesKey("notification_rationale_silenced")
        private val HAS_REQUESTED_NOTIFICATION_PERMISSION_KEY =
            booleanPreferencesKey("has_requested_notification_permission")
    }
}
