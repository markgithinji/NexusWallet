package com.example.nexuswallet.feature.core.domain.repository

import javax.crypto.Cipher

interface KeyStoreRepository {
    suspend fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray>
    suspend fun decrypt(encryptedData: ByteArray, iv: ByteArray): ByteArray
    fun isKeyStoreAvailable(): Boolean
    fun clearKey()

    /**
     * Get a Cipher initialized for decryption.
     * Use this when setUserAuthenticationRequired(true) is enabled.
     */
    fun getDecryptionCipher(iv: ByteArray): Cipher

    /**
     * Decrypt data using an already initialized and (potentially) unlocked Cipher.
     */
    fun decryptWithCipher(cipher: Cipher, encryptedData: ByteArray): ByteArray
}