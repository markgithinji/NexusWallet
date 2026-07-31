package com.example.nexuswallet.feature.core.data.util

import com.example.nexuswallet.feature.core.util.decodeHex
import com.example.nexuswallet.feature.core.util.toHex
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinHasher @Inject constructor() {

    /**
     * Hashes a PIN using PBKDF2WithHmacSHA256 with a unique salt.
     * Output format: <hash_hex>:<salt_hex>:<iterations>
     */
    fun hashPin(pin: String): String {
        val salt = generateSalt()
        val hash = pbkdf2(pin.toCharArray(), salt, ITERATIONS)
        return "${hash.toHex()}:${salt.toHex()}:$ITERATIONS"
    }

    /**
     * Verifies an input PIN against a stored hash string.
     * Supports both new PBKDF2 hashes (3 parts) and legacy SHA-256 hashes (2 parts).
     */
    fun verifyPin(inputPin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        return when (parts.size) {
            3 -> {
                // New PBKDF2 format: <hash>:<salt>:<iterations>
                val storedHashBytes = parts[0].decodeHex()
                val salt = parts[1].decodeHex()
                val iterations = parts[2].toInt()
                val inputHashBytes = pbkdf2(inputPin.toCharArray(), salt, iterations)
                constantTimeAreEqual(storedHashBytes, inputHashBytes)
            }
            2 -> {
                // Legacy SHA-256 format: <hash>:<salt>
                val storedHashPart = parts[0]
                val saltHex = parts[1]
                val inputHash = MessageDigest.getInstance("SHA-256")
                    .digest("$inputPin$saltHex".toByteArray())
                    .toHex()
                // Legacy didn't use constant time, but we can't easily change stored format now
                // for the SHA-256 check itself, but we can compare the result string/bytes safely.
                storedHashPart == inputHash
            }
            else -> false
        }
    }

    /**
     * Constant-time comparison to prevent timing attacks.
     */
    private fun constantTimeAreEqual(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].toInt() xor b[i].toInt())
        }
        return result == 0
    }

    private fun pbkdf2(pin: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, iterations, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun generateSalt(): ByteArray {
        return ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
    }

    companion object {
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 10000 // High enough to be slow, low enough for mobile UI
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
    }
}
