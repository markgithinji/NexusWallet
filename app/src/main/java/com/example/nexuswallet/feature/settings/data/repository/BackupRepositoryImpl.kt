package com.example.nexuswallet.feature.settings.data.repository

import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.settings.domain.model.BackupBundle
import com.example.nexuswallet.feature.settings.domain.repository.BackupRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val json: Json
) : BackupRepository {

    override suspend fun encryptBackup(bundle: BackupBundle, pin: String): Result<ByteArray> {
        return try {
            val bundleJson = json.encodeToString(bundle)
            val encryptedBytes = encrypt(bundleJson.toByteArray(), pin)
            Result.Success(encryptedBytes)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to encrypt backup")
        }
    }

    override suspend fun decryptBackup(backupData: ByteArray, pin: String): Result<BackupBundle> {
        return try {
            val decryptedBytes = decrypt(backupData, pin)
            val bundle = json.decodeFromString<BackupBundle>(decryptedBytes.decodeToString())
            Result.Success(bundle)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Failed to decrypt backup")
        }
    }

    // --- Encryption Helpers ---

    private fun encrypt(data: ByteArray, pin: String): ByteArray {
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        
        val encrypted = cipher.doFinal(data)
        
        // Output: SALT + IV + ENCRYPTED
        return salt + iv + encrypted
    }

    private fun decrypt(encryptedData: ByteArray, pin: String): ByteArray {
        if (encryptedData.size < SALT_SIZE + IV_SIZE) throw Exception("Invalid backup data")
        
        val salt = encryptedData.sliceArray(0 until SALT_SIZE)
        val iv = encryptedData.sliceArray(SALT_SIZE until SALT_SIZE + IV_SIZE)
        val encrypted = encryptedData.sliceArray(SALT_SIZE + IV_SIZE until encryptedData.size)
        
        val key = deriveKey(pin, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH, iv))
        
        return cipher.doFinal(encrypted)
    }

    private fun deriveKey(pin: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    companion object {
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_SIZE = 16
        private const val IV_SIZE = 12
        private const val TAG_LENGTH = 128
    }
}
