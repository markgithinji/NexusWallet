package com.example.nexuswallet.feature.authentication.data.repository

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.nexuswallet.feature.authentication.domain.KeyStoreRepository
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Handles encryption/decryption using Android KeyStore
 * Provides hardware-backed security when available
 */

@Singleton
class KeyStoreRepositoryImpl @Inject constructor(
    private val keyStore: KeyStore,
    private val ioDispatcher: CoroutineDispatcher
) : KeyStoreRepository {

    override suspend fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> =
        withContext(ioDispatcher) {
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

    override suspend fun decrypt(encryptedData: ByteArray, iv: ByteArray): ByteArray =
        withContext(ioDispatcher) {
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

                cipher.doFinal(encryptedData)
            } catch (e: Exception) {
                throw EncryptionException("Failed to decrypt data", e)
            }
        }

    override suspend fun encryptString(plaintext: String): Pair<String, String> =
        withContext(ioDispatcher) {
            val (encryptedBytes, iv) = encrypt(plaintext.toByteArray(Charsets.UTF_8))
            Pair(encryptedBytes.toHex(), iv.toHex())
        }

    override suspend fun decryptString(encryptedHex: String, ivHex: String): String =
        withContext(ioDispatcher) {
            val encryptedBytes = hexToBytes(encryptedHex)
            val iv = hexToBytes(ivHex)
            val decryptedBytes = decrypt(encryptedBytes, iv)
            String(decryptedBytes, Charsets.UTF_8)
        }

    override fun isKeyStoreAvailable(): Boolean {
        return try {
            getSecretKey()
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun clearKey() {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
        } catch (e: Exception) {
            // Ignore
        }
    }

    /**
     * Get or create the secret key from Android KeyStore
     */
    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

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
            .setUserAuthenticationRequired(false)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexus_wallet_master_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
    }
}