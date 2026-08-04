package com.example.nexuswallet.feature.settings.domain.repository

import com.example.nexuswallet.feature.settings.domain.model.SupportedCurrency
import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Authentication
    suspend fun storePinHash(pinHash: String)
    suspend fun getPinHash(): String?
    fun observePinHash(): Flow<String?>
    suspend fun clearPinHash()
    
    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun isBiometricEnabled(): Boolean
    fun observeBiometricEnabled(): Flow<Boolean>

    suspend fun saveLastAuthenticationTime(timestamp: Long)
    suspend fun getLastAuthenticationTime(): Long?

    // Privacy & Security Settings
    suspend fun setPrivacyModeEnabled(enabled: Boolean)
    suspend fun isPrivacyModeEnabled(): Boolean
    fun observePrivacyModeEnabled(): Flow<Boolean>

    suspend fun setRequireAuthForSend(enabled: Boolean)
    suspend fun isRequireAuthForSend(): Boolean
    fun observeRequireAuthForSend(): Flow<Boolean>

    // General Preferences
    suspend fun setSelectedCurrency(currency: SupportedCurrency)
    suspend fun getSelectedCurrency(): SupportedCurrency
    fun observeSelectedCurrency(): Flow<SupportedCurrency>

    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun getThemeMode(): ThemeMode
    fun observeThemeMode(): Flow<ThemeMode>

    // Notifications
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun isNotificationsEnabled(): Boolean
    fun observeNotificationsEnabled(): Flow<Boolean>

    suspend fun setNotificationRationaleSilenced(silenced: Boolean)
    suspend fun isNotificationRationaleSilenced(): Boolean
    fun observeNotificationRationaleSilenced(): Flow<Boolean>

    suspend fun setHasRequestedNotificationPermission(requested: Boolean)
    suspend fun hasRequestedNotificationPermission(): Boolean

    suspend fun clearAll()
}
