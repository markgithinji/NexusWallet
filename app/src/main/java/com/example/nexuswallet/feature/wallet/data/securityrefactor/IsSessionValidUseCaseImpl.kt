package com.example.nexuswallet.feature.wallet.data.securityrefactor

import com.example.nexuswallet.feature.authentication.domain.SecurityPreferencesRepository
import com.example.nexuswallet.feature.coin.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsSessionValidUseCaseImpl @Inject constructor(
    private val securityPreferencesRepository: SecurityPreferencesRepository,
    private val logger: Logger
) : IsSessionValidUseCase {

    private val tag = "IsSessionValidUC"

    override suspend fun invoke(): Result<Boolean> {
        val lastAuthTime = securityPreferencesRepository.getLastAuthenticationTime()

        if (lastAuthTime == null) {
            logger.d(tag, "No previous authentication found")
            return Result.Success(false)
        }

        val isValid = System.currentTimeMillis() - lastAuthTime < SESSION_TIMEOUT_MILLIS
        logger.d(tag, "Session valid: $isValid (last auth: $lastAuthTime)")

        return Result.Success(isValid)
    }

    companion object {
        private const val SESSION_TIMEOUT_SECONDS = 10
        private const val SESSION_TIMEOUT_MILLIS = SESSION_TIMEOUT_SECONDS * 1000L
    }
}