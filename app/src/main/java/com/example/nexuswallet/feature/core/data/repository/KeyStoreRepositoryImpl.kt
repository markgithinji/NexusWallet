package com.example.nexuswallet.feature.core.data.repository

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.nexuswallet.feature.core.data.util.safeKeyStoreCall
import com.example.nexuswallet.feature.core.domain.repository.KeyStoreRepository
import com.example.nexuswallet.feature.core.util.toHex
import com.example.nexuswallet.feature.core.domain.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles encryption/decryption using Android KeyStore
 * Provides hardware-backed security when available
 */
@Singleton
class KeyStoreRepositoryImpl @Inject constructor(
    private val keyStore: KeyStore,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : KeyStoreRepository {

    override suspend fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> =
        withContext(ioDispatcher) {
            safeKeyStoreCall {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())

                val iv = cipher.iv
                val encrypted = cipher.doFinal(plaintext)
                
                Pair(encrypted, iv)
            }
        }

    override suspend fun decrypt(encryptedData: ByteArray, iv: ByteArray): ByteArray =
        withContext(ioDispatcher) {
            safeKeyStoreCall {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)

                cipher.doFinal(encryptedData)
            }
        }

    override fun isKeyStoreAvailable(): Boolean {
        return try {
            keyStore.containsAlias(KEY_ALIAS) &&
                    keyStore.getKey(KEY_ALIAS, null) != null
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

    override fun getEncryptionCipher(): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        return cipher
    }

    override fun getDecryptionCipher(iv: ByteArray): Cipher {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher
    }

    override fun encryptWithCipher(cipher: Cipher, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val encrypted = cipher.doFinal(plaintext)
        val iv = cipher.iv
        return Pair(encrypted, iv)
    }

    override fun decryptWithCipher(cipher: Cipher, encryptedData: ByteArray): ByteArray {
        return cipher.doFinal(encryptedData)
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

        val builder = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE)

        // Only require authentication if biometrics are enrolled
        val biometricManager = BiometricManager.from(context)
        val canAuthenticate = biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

        if (canAuthenticate) {
            builder.setUserAuthenticationRequired(true)
            builder.setInvalidatedByBiometricEnrollment(true)

            // Use a 5-second validity duration.
            // This allows batch operations like Wallet Creation to succeed with one touch,
            // while minimizing the hardware's "unlocked" window.
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(5)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(5, KeyProperties.AUTH_BIOMETRIC_STRONG)
            }
        }

        // Use StrongBox if available (Android 9+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val hasStrongBox = context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            if (hasStrongBox) {
                builder.setIsStrongBoxBacked(true)
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexus_wallet_master_key_v5"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256
        private const val GCM_TAG_LENGTH = 128
    }
}