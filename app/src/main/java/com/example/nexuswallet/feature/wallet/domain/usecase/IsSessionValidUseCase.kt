package com.example.nexuswallet.feature.wallet.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SettingsRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IsSessionValidUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(): Result<Boolean> {
        val lastAuthTime = settingsRepository.getLastAuthenticationTime()

        if (lastAuthTime == null) {
            logger.d(TAG, "No previous authentication found")
            return Result.Success(false)
        }

        val isValid = System.currentTimeMillis() - lastAuthTime < SESSION_TIMEOUT_MILLIS
        logger.d(TAG, "Session valid: $isValid (last auth: $lastAuthTime)")

        return Result.Success(isValid)
    }

    companion object {
        private const val TAG = "IsSessionValidUC"
        private const val SESSION_TIMEOUT_SECONDS = 10
        private const val SESSION_TIMEOUT_MILLIS = SESSION_TIMEOUT_SECONDS * 1000L
    }
}