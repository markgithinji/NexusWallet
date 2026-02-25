package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.data.repository.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.wallet.data.securityrefactor.SecurityConstants.SESSION_TIMEOUT_MILLIS
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsSessionValidUseCase @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository
) {
    suspend operator fun invoke(): Result<Boolean> {
        return try {
            val lastAuthTime = securityPreferencesRepository.getLastAuthenticationTime()
                ?: return Result.Success(false)

            val isValid = System.currentTimeMillis() - lastAuthTime < SESSION_TIMEOUT_MILLIS
            Result.Success(isValid)
        } catch (e: Exception) {
            Result.Error("Failed to check session validity: ${e.message}", e)
        }
    }
}

object SecurityConstants {
    const val SESSION_TIMEOUT_SECONDS = 10
    const val SESSION_TIMEOUT_MILLIS = SESSION_TIMEOUT_SECONDS * 1000L
}
