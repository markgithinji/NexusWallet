package com.example.nexuswallet.feature.authentication.domain

import com.example.nexuswallet.feature.logging.Logger
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import com.example.nexuswallet.feature.coin.Result

@Singleton
class VerifyPinUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : VerifyPinUseCase {

    private val tag = "VerifyPin"

    override suspend fun invoke(pin: String): Result<Boolean> {
        val startTime = System.currentTimeMillis()

        val storedHash = securityPreferencesRepository.getPinHash()
        if (storedHash == null) {
            logger.d(tag, "No PIN set")
            return Result.Success(false)
        }

        val isValid = verifyPinHash(pin, storedHash)
        val duration = System.currentTimeMillis() - startTime

        if (isValid) {
            logger.d(tag, "PIN verified | duration=${duration}ms")
            return Result.Success(true)
        } else {
            logger.w(tag, "PIN verification failed | duration=${duration}ms")
            return Result.Success(false)
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
class RecordAuthenticationUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : RecordAuthenticationUseCase {

    private val tag = "RecordAuth"

    override suspend fun invoke(): Result<Unit> {
        val timestamp = System.currentTimeMillis()
        securityPreferencesRepository.saveLastAuthenticationTime(timestamp)
        logger.d(tag, "Authentication recorded | timestamp=$timestamp")
        return Result.Success(Unit)
    }
}