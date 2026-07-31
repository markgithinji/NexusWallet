package com.example.nexuswallet.feature.settings.domain.usecase

import com.example.nexuswallet.feature.settings.domain.repository.SecurityRepository
import com.example.nexuswallet.feature.core.util.Result
import com.example.nexuswallet.feature.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordAuthenticationUseCase @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val logger: Logger
) {

    suspend operator fun invoke(): Result<Unit> {
        val timestamp = System.currentTimeMillis()
        securityRepository.saveLastAuthenticationTime(timestamp)
        logger.d(TAG, "Authentication recorded | timestamp=$timestamp")
        return Result.Success(Unit)
    }

    companion object {
        private const val TAG = "RecordAuth"
    }
}