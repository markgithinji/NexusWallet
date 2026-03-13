package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.authentication.domain.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import com.example.nexuswallet.toHex
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SetPinUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) {
    suspend operator fun invoke(pin: String): Result<Boolean> {
        val pinHash = hashPin(pin)
        securityPreferencesRepository.storePinHash(pinHash)
        val success = securityPreferencesRepository.getPinHash() != null
        logger.d("SetPinUseCase", "PIN set successfully: $success")
        return Result.Success(success)
    }

    private fun hashPin(pin: String): String {
        val salt = generateSalt()
        val hash = MessageDigest.getInstance("SHA-256")
            .digest("$pin${salt.toHex()}".toByteArray())
        return "${hash.toHex()}:${salt.toHex()}"
    }

    private fun generateSalt(): ByteArray {
        return ByteArray(16).also { SecureRandom().nextBytes(it) }
    }
}