package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinManager @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend fun setPin(pin: String): Boolean {
        return try {
            val pinHash = hashPin(pin)
            securityPreferencesRepository.storePinHash(pinHash)
            securityPreferencesRepository.getPinHash() != null
        } catch (e: Exception) {
            false
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        return try {
            val storedHash = securityPreferencesRepository.getPinHash() ?: return false
            verifyPinHash(pin, storedHash)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun isPinSet(): Boolean {
        return securityPreferencesRepository.getPinHash() != null
    }

    suspend fun clearPin() {
        securityPreferencesRepository.clearPinHash()
    }

    private fun hashPin(pin: String): String {
        val salt = generateSalt()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$pin${salt.toHex()}".toByteArray())
        return "${hash.toHex()}:${salt.toHex()}"
    }

    private fun verifyPinHash(inputPin: String, storedHash: String): Boolean {
        val parts = storedHash.split(":")
        if (parts.size != 2) return false

        val (storedHashPart, saltHex) = parts
        val inputHash = MessageDigest.getInstance("SHA-256")
            .digest("$inputPin$saltHex".toByteArray())
            .toHex()

        return inputHash == storedHashPart
    }

    private fun generateSalt(): ByteArray {
        return ByteArray(16).also { SecureRandom().nextBytes(it) }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}