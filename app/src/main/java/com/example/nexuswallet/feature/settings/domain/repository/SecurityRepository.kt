package com.example.nexuswallet.feature.settings.domain.repository

import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SecurityRepository {
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
    suspend fun setSelectedCurrency(currencyCode: String)
    suspend fun getSelectedCurrency(): String
    fun observeSelectedCurrency(): Flow<String>

    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun getThemeMode(): ThemeMode
    fun observeThemeMode(): Flow<ThemeMode>

    suspend fun clearAll()
}
