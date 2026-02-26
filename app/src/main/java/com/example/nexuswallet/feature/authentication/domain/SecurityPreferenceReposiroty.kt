package com.example.nexuswallet.feature.authentication.domain

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
}