package com.example.nexuswallet.feature.core.domain.repository

import javax.crypto.Cipher

interface KeyStoreRepository {
    suspend fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray>
    suspend fun decrypt(encryptedData: ByteArray, iv: ByteArray): ByteArray
    fun isKeyStoreAvailable(): Boolean
    fun clearKey()

    /**
     * Get a Cipher initialized for encryption.
     * Use this when setUserAuthenticationRequired(true) is enabled.
     */
    fun getEncryptionCipher(): Cipher

    /**
     * Get a Cipher initialized for decryption.
     * Use this when setUserAuthenticationRequired(true) is enabled.
     */
    fun getDecryptionCipher(iv: ByteArray): Cipher

    /**
     * Encrypt data using an already initialized and (potentially) unlocked Cipher.
     */
    fun encryptWithCipher(cipher: Cipher, plaintext: ByteArray): Pair<ByteArray, ByteArray>

    /**
     * Decrypt data using an already initialized and (potentially) unlocked Cipher.
     */
    fun decryptWithCipher(cipher: Cipher, encryptedData: ByteArray): ByteArray
}