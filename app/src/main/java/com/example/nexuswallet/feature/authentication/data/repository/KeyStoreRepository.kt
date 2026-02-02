package com.example.nexuswallet.feature.authentication.data.repository


import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles encryption/decryption using Android KeyStore
 * Provides hardware-backed security when available
 */
class KeyStoreRepository(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexus_wallet_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * Get or create the secret key from Android KeyStore
     */
    private fun getSecretKey(): SecretKey {
        // Try to get existing key
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        // Create new key if doesn't exist
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)
            .setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(30)
            .setKeyValidityForOriginationEnd(null)
            .setKeyValidityForConsumptionEnd(null)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Encrypt data using Android KeyStore
     * @param plaintext The data to encrypt
     * @return Pair of encrypted bytes and IV
     */
    suspend fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext)

            Pair(encrypted, iv)
        } catch (e: Exception) {
            throw EncryptionException("Failed to encrypt data", e)
        }
    }

    /**
     * Decrypt data using Android KeyStore
     * @param encryptedData The encrypted data
     * @param iv Initialization vector
     * @return Decrypted plaintext
     */
    suspend fun decrypt(encryptedData: ByteArray, iv: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

            cipher.doFinal(encryptedData)
        } catch (e: Exception) {
            throw EncryptionException("Failed to decrypt data", e)
        }
    }

    /**
     * Encrypt string data
     */
    suspend fun encryptString(plaintext: String): Pair<String, String> {
        val (encryptedBytes, iv) = encrypt(plaintext.toByteArray(Charsets.UTF_8))
        return Pair(encryptedBytes.toHex(), iv.toHex())
    }

    /**
     * Decrypt string data
     */
    suspend fun decryptString(encryptedHex: String, ivHex: String): String {
        val encryptedBytes = hexToBytes(encryptedHex)
        val iv = hexToBytes(ivHex)
        val decryptedBytes = decrypt(encryptedBytes, iv)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    /**
     * Check if KeyStore is available and ready
     */
    fun isKeyStoreAvailable(): Boolean {
        return try {
            getSecretKey() // This will attempt to get/create key
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clear the key from KeyStore
     */
    fun clearKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // Ignore
        }
    }

    // Utility functions
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}

class EncryptionException(message: String, cause: Throwable? = null) : Exception(message, cause)