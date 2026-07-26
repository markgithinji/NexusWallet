package com.example.nexuswallet.feature.authentication.domain.repository

import com.example.nexuswallet.feature.settings.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SecurityPreferencesRepository {
    suspend fun storeEncryptedMnemonic(walletId: String, encryptedMnemonic: String, iv: ByteArray)
    suspend fun getEncryptedMnemonic(walletId: String): Pair<String, ByteArray>?

    suspend fun storeEncryptedPrivateKey(walletId: String, keyType: String, encryptedKey: String, iv: ByteArray)
    suspend fun getEncryptedPrivateKey(walletId: String, keyType: String): Pair<String, ByteArray>?

    suspend fun getEncryptedBackup(walletId: String): Pair<String, ByteArray>?

    suspend fun storePinHash(pinHash: String)
    suspend fun getPinHash(): String?

    suspend fun setBiometricEnabled(enabled: Boolean)
    suspend fun isBiometricEnabled(): Boolean

    suspend fun clearAll()
    suspend fun clearPinHash()

    suspend fun saveLastAuthenticationTime(timestamp: Long)
    suspend fun getLastAuthenticationTime(): Long?

    suspend fun setPrivacyModeEnabled(enabled: Boolean)
    suspend fun isPrivacyModeEnabled(): Boolean

    suspend fun setRequireAuthForSend(enabled: Boolean)
    suspend fun isRequireAuthForSend(): Boolean

    suspend fun setSelectedCurrency(currencyCode: String)
    suspend fun getSelectedCurrency(): String

    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun getThemeMode(): ThemeMode

    fun observePinHash(): Flow<String?>
    fun observeBiometricEnabled(): Flow<Boolean>
    fun observePrivacyModeEnabled(): Flow<Boolean>
    fun observeRequireAuthForSend(): Flow<Boolean>
    fun observeSelectedCurrency(): Flow<String>
    fun observeThemeMode(): Flow<ThemeMode>
}