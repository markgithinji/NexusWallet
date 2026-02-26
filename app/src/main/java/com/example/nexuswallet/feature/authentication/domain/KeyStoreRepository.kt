package com.example.nexuswallet.feature.authentication.domain

interface KeyStoreRepository {
    suspend fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray>
    suspend fun decrypt(encryptedData: ByteArray, iv: ByteArray): ByteArray
    suspend fun encryptString(plaintext: String): Pair<String, String>
    suspend fun decryptString(encryptedHex: String, ivHex: String): String
    fun isKeyStoreAvailable(): Boolean
    fun clearKey()
}