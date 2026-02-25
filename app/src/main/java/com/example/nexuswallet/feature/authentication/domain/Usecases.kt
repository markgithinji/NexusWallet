package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VerifyPinUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        return try {
            val storedHash =
                securityPreferencesRepository.getPinHash() ?: return Result.Success(false)
            val isValid = verifyPinHash(pin, storedHash)
            Result.Success(isValid)
        } catch (e: Exception) {
            Result.Error("Failed to verify PIN: ${e.message}", e)
        }
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

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}

@Singleton
class RecordAuthenticationUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(): Result<Unit> {
        return try {
            // Store last authentication time in DataStore
            securityPreferencesRepository.saveLastAuthenticationTime(System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Failed to record authentication: ${e.message}", e)
        }
    }
}